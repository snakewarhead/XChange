package org.knowm.xchange.huobi;

import org.knowm.xchange.huobi.dto.HuobiStringResult;
import org.knowm.xchange.huobi.dto.trade.HuobiFutureCreateOrderRequest;
import si.mazi.rescu.ParamsDigest;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

/**
 * Created by lin on 2021-01-29.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public interface HuobiContract extends HuobiFutureBase {

  @POST
  @Path("/v1/contract_order")
  @Consumes(MediaType.APPLICATION_JSON)
  HuobiStringResult placeOrder(
      HuobiFutureCreateOrderRequest body,
      @QueryParam("SignatureMethod") String signatureMethod,
      @QueryParam("SignatureVersion") int signatureVersion,
      @QueryParam("Timestamp") String nonce,
      @QueryParam("AccessKeyId") String apiKey,
      @QueryParam("Signature") ParamsDigest signature)
      throws IOException;

}
