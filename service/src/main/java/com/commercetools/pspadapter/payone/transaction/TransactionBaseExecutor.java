package com.commercetools.pspadapter.payone.transaction;

import com.commercetools.pspadapter.payone.domain.payone.model.common.ResponseErrorCode;
import com.commercetools.pspadapter.payone.domain.payone.model.common.ResponseStatus;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import io.sphere.sdk.json.SphereJsonUtils;
import io.sphere.sdk.payments.commands.updateactions.SetStatusInterfaceCode;
import io.sphere.sdk.payments.commands.updateactions.SetStatusInterfaceText;
import io.sphere.sdk.types.Type;

import java.util.Map;

/**
 * @author mht@dotsource.de
 * Common base class responsible for default paymentupdateactions
 */
public abstract class TransactionBaseExecutor extends IdempotentTransactionExecutor{

    public final static String STATUS = "status";
    public final static String ERROR_CODE = "errorcode";
    public final static String CUSTOMER_MESSAGE = "customermessage";
    public final static String ERROR_MESSAGE = "errormessage";
    public final static String ERROR = "ERROR";

    public TransactionBaseExecutor(LoadingCache<String, Type> typeCache) {
        super(typeCache);
    }

    /**
     * Creates the SetStatusInterfaceCode from the response
     * @param response contains all key that creates the
     * @return the UpdateAction that
     */
    protected SetStatusInterfaceCode setStatusInterfaceCode(final Map<String, String> response){
        final String status = response.get(STATUS);
        final StringBuilder stringBuilder = new StringBuilder(status);
        if(ERROR.equals(status)) {
            //Payone errorcode is required for error case
            stringBuilder.append(" ");
            stringBuilder.append(response.get(ERROR_CODE));
        }
        return SetStatusInterfaceCode.of(stringBuilder.toString());
    }

    /**
     * Creates the SetStatusInterfaceText from the response ErrorMessage in case of Error
     * @param response contains all key that creates the
     * @return the UpdateAction that
     */
    protected SetStatusInterfaceText setStatusInterfaceText(final Map<String, String> response){
        final String status = response.get(STATUS);
        final StringBuilder stringBuilder = new StringBuilder("");
        if(ERROR.equals(status)) {
            stringBuilder.append(response.get(ERROR_MESSAGE));
        }
        else {
            stringBuilder.append(status);
        }
        return SetStatusInterfaceText.of(stringBuilder.toString());
    }

    /**
     * Converts {@code Map<String, String>} response value to valid JSON string.
     * @param response response map to convert
     * @return JSON string with respective {@code response} key-value entries.
     * @see #exceptionToResponseJsonString(Exception)
     */
    protected static String responseToJsonString(Map<String, String> response) {
        return SphereJsonUtils.toJsonString(response);
    }

    /**
     * In case an error occurred on service side, but not in Payone, we simulate same response structure,
     * as described in <i>TECHNICAL REFERENCE PAYONE Platform Channel Server API, Version 2.77</i>:<ul>
     *     <li><b>status</b>: ERROR</li>
     *     <li><b>errorcode</b>: {@link ResponseErrorCode#TRANSACTION_EXCEPTION}</li>
     *     <li><b>errormessage</b>: {@code exception.getMessage()} value</li>
     *     <li><b>customermessage</b>: Neutral English message to display to customer on a page.</li>
     * </ul>
     * @param exception exception which occurred when transaction executed.
     * @return JSON string with key-value entries like in Payone API.
     */
    protected static String exceptionToResponseJsonString(Exception exception) {
        return responseToJsonString(ImmutableMap.of(
                STATUS, ResponseStatus.ERROR.getStateCode(),
                ERROR_CODE, ResponseErrorCode.TRANSACTION_EXCEPTION.getErrorCode(),
                ERROR_MESSAGE, "Integration Service Exception: " + exception.getMessage(),
                CUSTOMER_MESSAGE, "An error occurred while processing this transaction"));
    }
}
