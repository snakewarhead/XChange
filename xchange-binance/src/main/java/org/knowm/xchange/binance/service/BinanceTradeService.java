package org.knowm.xchange.binance.service;

import org.apache.commons.lang3.StringUtils;
import org.knowm.xchange.binance.BinanceAdapters;
import org.knowm.xchange.binance.BinanceAuthenticated;
import org.knowm.xchange.binance.BinanceErrorAdapter;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.binance.dto.BinanceException;
import org.knowm.xchange.binance.dto.trade.*;
import org.knowm.xchange.client.ResilienceRegistries;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.Order;
import org.knowm.xchange.dto.marketdata.Trades;
import org.knowm.xchange.dto.trade.*;
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotAvailableFromExchangeException;
import org.knowm.xchange.service.trade.TradeService;
import org.knowm.xchange.service.trade.params.*;
import org.knowm.xchange.service.trade.params.orders.*;
import org.knowm.xchange.utils.Assert;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class BinanceTradeService extends BinanceTradeServiceRaw implements TradeService {

  public BinanceTradeService(
      BinanceExchange exchange,
      BinanceAuthenticated binance,
      ResilienceRegistries resilienceRegistries) {
    super(exchange, binance, resilienceRegistries);
  }

  @Override
  public OpenOrders getOpenOrders() throws IOException {
    return getOpenOrders(new DefaultOpenOrdersParam());
  }

  public OpenOrders getOpenOrders(CurrencyPair pair) throws IOException {
    return getOpenOrders(new DefaultOpenOrdersParamCurrencyPair(pair));
  }

  @Override
  public OpenOrders getOpenOrders(OpenOrdersParams params) throws IOException {
    try {
      List<BinanceOrder> binanceOpenOrders;
      if (params instanceof OpenOrdersParamCurrencyPair) {
        OpenOrdersParamCurrencyPair pairParams = (OpenOrdersParamCurrencyPair) params;
        CurrencyPair pair = pairParams.getCurrencyPair();
        binanceOpenOrders = super.openOrders(pair);
      } else {
        binanceOpenOrders = super.openOrders();
      }

      List<LimitOrder> limitOrders = new ArrayList<>();
      List<Order> otherOrders = new ArrayList<>();
      binanceOpenOrders.forEach(
          binanceOrder -> {
            Order order = BinanceAdapters.adaptOrder(binanceOrder);
            if (order instanceof LimitOrder) {
              limitOrders.add((LimitOrder) order);
            } else {
              otherOrders.add(order);
            }
          });
      return new OpenOrders(limitOrders, otherOrders);
    } catch (BinanceException e) {
      throw BinanceErrorAdapter.adapt(e);
    }
  }

  public OpenOrders getOpenOrders(CurrencyPair pair, String apiKey, String secretKey) throws IOException {
    try {
      List<BinanceOrder> binanceOpenOrders = super.openOrders(pair, apiKey, BinanceHmacDigest.createInstance(secretKey));

      List<LimitOrder> limitOrders = new ArrayList<>();
      List<Order> otherOrders = new ArrayList<>();
      binanceOpenOrders.forEach(
          binanceOrder -> {
            Order order = BinanceAdapters.adaptOrder(binanceOrder);
            if (order instanceof LimitOrder) {
              limitOrders.add((LimitOrder) order);
            } else {
              otherOrders.add(order);
            }
          });
      return new OpenOrders(limitOrders, otherOrders);
    } catch (BinanceException e) {
      throw BinanceErrorAdapter.adapt(e);
    }
  }

  @Override
  public String placeMarketOrder(MarketOrder mo) throws IOException {
    return placeOrder(OrderType.MARKET, mo, null, null, null, null, null);
  }

  @Override
  public String placeLimitOrder(LimitOrder limitOrder) throws IOException {
    TimeInForce tif = BinanceAdapters.timeInForceFromOrder(limitOrder).orElse(TimeInForce.GTC);
    OrderType type;
    if (limitOrder.hasFlag(BinanceOrderFlags.LIMIT_MAKER)) {
      type = OrderType.LIMIT_MAKER;
      tif = null;
    } else {
      type = OrderType.LIMIT;
    }
    return placeOrder(type, limitOrder, limitOrder.getLimitPrice(), null, tif, null, null);
  }

  @Override
  public String placeStopOrder(StopOrder order) throws IOException {
    // Time-in-force should not be provided for market orders but is required for
    // limit orders, order we only default it for limit orders. If the caller
    // specifies one for a market order, we don't remove it, since Binance might allow
    // it at some point.
    TimeInForce tif =
        BinanceAdapters.timeInForceFromOrder(order).orElse(order.getLimitPrice() != null ? TimeInForce.GTC : null);

    OrderType orderType = BinanceAdapters.adaptOrderType(order);

    return placeOrder(orderType, order, order.getLimitPrice(), order.getStopPrice(), tif, null, null);
  }

  public String placeOrder(
      OrderType type, Order order, BigDecimal limitPrice, BigDecimal stopPrice, TimeInForce tif, String apiKey, String secretKey)
      throws IOException {
    try {
      Long recvWindow =
          (Long)
              exchange.getExchangeSpecification().getExchangeSpecificParametersItem("recvWindow");

      BinanceNewOrder newOrder = null;
      if (StringUtils.isNotEmpty(apiKey) && StringUtils.isNotEmpty(secretKey)) {
        newOrder =
            newOrder(
                order.getCurrencyPair(),
                BinanceAdapters.convert(order.getType()),
                type,
                tif,
                order.getOriginalAmount(),
                limitPrice,
                order.getUserReference(),
                stopPrice,
                null,
                apiKey,
                BinanceHmacDigest.createInstance(secretKey));
      } else {
        newOrder =
            newOrder(
                order.getCurrencyPair(),
                BinanceAdapters.convert(order.getType()),
                type,
                tif,
                order.getOriginalAmount(),
                limitPrice,
                order.getUserReference(),
                stopPrice,
                null);
      }
      return Long.toString(newOrder.orderId);
    } catch (BinanceException e) {
      throw BinanceErrorAdapter.adapt(e);
    }
  }

  public void placeTestOrder(
      OrderType type, Order order, BigDecimal limitPrice, BigDecimal stopPrice, String apiKey, String secretKey) throws IOException {
    try {
      TimeInForce tif = BinanceAdapters.timeInForceFromOrder(order).orElse(null);
      Long recvWindow =
          (Long)
              exchange.getExchangeSpecification().getExchangeSpecificParametersItem("recvWindow");

      if (StringUtils.isNotEmpty(apiKey) && StringUtils.isNotEmpty(secretKey)) {
        testNewOrder(
            order.getCurrencyPair(),
            BinanceAdapters.convert(order.getType()),
            type,
            tif,
            order.getOriginalAmount(),
            limitPrice,
            order.getUserReference(),
            stopPrice,
            null,
            apiKey,
            BinanceHmacDigest.createInstance(secretKey));
      } else {
        testNewOrder(
            order.getCurrencyPair(),
            BinanceAdapters.convert(order.getType()),
            type,
            tif,
            order.getOriginalAmount(),
            limitPrice,
            order.getUserReference(),
            stopPrice,
            null);
      }
    } catch (BinanceException e) {
      throw BinanceErrorAdapter.adapt(e);
    }
  }

  @Override
  public boolean cancelOrder(String orderId) {
    throw new ExchangeException("You need to provide the currency pair to cancel an order.");
  }

  @Override
  public boolean cancelOrder(CancelOrderParams params) throws IOException {
    try {
      if (!(params instanceof CancelOrderByCurrencyPair)
          && !(params instanceof CancelOrderByIdParams)) {
        throw new ExchangeException(
            "You need to provide the currency pair and the order id to cancel an order.");
      }
      CancelOrderByCurrencyPair paramCurrencyPair = (CancelOrderByCurrencyPair) params;
      CancelOrderByIdParams paramId = (CancelOrderByIdParams) params;
      super.cancelOrder(
          paramCurrencyPair.getCurrencyPair(),
          BinanceAdapters.id(paramId.getOrderId()),
          null,
          null);
      return true;
    } catch (BinanceException e) {
      throw BinanceErrorAdapter.adapt(e);
    }
  }

  @Override
  public UserTrades getTradeHistory(TradeHistoryParams params) throws IOException {
    try {
      Assert.isTrue(
          params instanceof TradeHistoryParamCurrencyPair,
          "You need to provide the currency pair to get the user trades.");
      TradeHistoryParamCurrencyPair pairParams = (TradeHistoryParamCurrencyPair) params;
      CurrencyPair pair = pairParams.getCurrencyPair();
      if (pair == null) {
        throw new ExchangeException(
            "You need to provide the currency pair to get the user trades.");
      }

      Integer limit = null;
      if (params instanceof TradeHistoryParamLimit) {
        TradeHistoryParamLimit limitParams = (TradeHistoryParamLimit) params;
        limit = limitParams.getLimit();
      }
      Long fromId = null;
      if (params instanceof TradeHistoryParamsIdSpan) {
        TradeHistoryParamsIdSpan idParams = (TradeHistoryParamsIdSpan) params;

        try {
          fromId = BinanceAdapters.id(idParams.getStartId());
        } catch (Throwable ignored) {
        }
      }

      Long startTime = null;
      Long endTime = null;
      if (params instanceof TradeHistoryParamsTimeSpan) {
        if (((TradeHistoryParamsTimeSpan) params).getStartTime() != null) {
          startTime = ((TradeHistoryParamsTimeSpan) params).getStartTime().getTime();
        }
        if (((TradeHistoryParamsTimeSpan) params).getEndTime() != null) {
          endTime = ((TradeHistoryParamsTimeSpan) params).getEndTime().getTime();
        }
      }
      if ((fromId != null) && (startTime != null || endTime != null))
        throw new ExchangeException(
            "You should either specify the id from which you get the user trades from or start and end times. If you specify both, Binance will only honour the fromId parameter.");

      List<BinanceTrade> binanceTrades = null;

      String apiKey = null;
      String secretKey = null;
      if (params instanceof TradeHistoryParamAuthenticity) {
        apiKey = ((TradeHistoryParamAuthenticity) params).getApiKey();
        secretKey = ((TradeHistoryParamAuthenticity) params).getSecretKey();
      }
      if (StringUtils.isNotEmpty(apiKey) && StringUtils.isNotEmpty(secretKey)) {
        binanceTrades = super.myTrades(pair, limit, startTime, endTime, fromId, apiKey, BinanceHmacDigest.createInstance(secretKey));
      } else {
        binanceTrades = super.myTrades(pair, limit, startTime, endTime, fromId);
      }

      List<UserTrade> trades =
          binanceTrades.stream()
              .map(
                  t ->
                      new UserTrade.Builder()
                          .type(BinanceAdapters.convertType(t.isBuyer))
                          .originalAmount(t.qty)
                          .currencyPair(pair)
                          .price(t.price)
                          .timestamp(t.getTime())
                          .id(Long.toString(t.id))
                          .orderId(Long.toString(t.orderId))
                          .feeAmount(t.commission)
                          .feeCurrency(Currency.getInstance(t.commissionAsset))
                          .build())
              .collect(Collectors.toList());
      long lastId = binanceTrades.stream().map(t -> t.id).max(Long::compareTo).orElse(0L);
      return new UserTrades(trades, lastId, Trades.TradeSortType.SortByTimestamp);
    } catch (BinanceException e) {
      throw BinanceErrorAdapter.adapt(e);
    }
  }

  @Override
  public TradeHistoryParams createTradeHistoryParams() {

    return new BinanceTradeHistoryParams();
  }

  @Override
  public OpenOrdersParams createOpenOrdersParams() {

    return new DefaultOpenOrdersParamCurrencyPair();
  }

  @Override
  public Collection<Order> getOrder(String... orderIds) {

    throw new NotAvailableFromExchangeException();
  }

  @Override
  public Collection<Order> getOrder(OrderQueryParams... params) throws IOException {
    try {
      Collection<Order> orders = new ArrayList<>();
      for (OrderQueryParams param : params) {
        if (!(param instanceof OrderQueryParamCurrencyPair)) {
          throw new ExchangeException(
              "Parameters must be an instance of OrderQueryParamCurrencyPair");
        }
        OrderQueryParamCurrencyPair orderQueryParamCurrencyPair =
            (OrderQueryParamCurrencyPair) param;
        if (orderQueryParamCurrencyPair.getCurrencyPair() == null
            || orderQueryParamCurrencyPair.getOrderId() == null) {
          throw new ExchangeException(
              "You need to provide the currency pair and the order id to query an order.");
        }

        orders.add(
            BinanceAdapters.adaptOrder(
                super.orderStatus(
                    orderQueryParamCurrencyPair.getCurrencyPair(),
                    BinanceAdapters.id(orderQueryParamCurrencyPair.getOrderId()),
                    null)));
      }
      return orders;
    } catch (BinanceException e) {
      throw BinanceErrorAdapter.adapt(e);
    }
  }

}
