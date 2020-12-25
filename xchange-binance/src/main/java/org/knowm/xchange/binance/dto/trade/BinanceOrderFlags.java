package org.knowm.xchange.binance.dto.trade;

import org.knowm.xchange.dto.Order.IOrderFlags;

public enum BinanceOrderFlags implements IOrderFlags {
  LIMIT_MAKER,
  CLOSE_POSITION,
  PRICE_PROTECT,
  WORKING_TYPE
  ;
}
