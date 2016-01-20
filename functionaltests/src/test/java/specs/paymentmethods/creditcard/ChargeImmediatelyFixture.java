package specs.paymentmethods.creditcard;

import com.commercetools.pspadapter.payone.domain.ctp.BlockingClient;
import com.commercetools.pspadapter.payone.domain.ctp.CustomTypeBuilder;
import com.commercetools.pspadapter.payone.mapping.CustomFieldKeys;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.sphere.sdk.payments.Payment;
import io.sphere.sdk.payments.PaymentDraft;
import io.sphere.sdk.payments.PaymentDraftBuilder;
import io.sphere.sdk.payments.PaymentMethodInfoBuilder;
import io.sphere.sdk.payments.TransactionDraft;
import io.sphere.sdk.payments.TransactionDraftBuilder;
import io.sphere.sdk.payments.TransactionState;
import io.sphere.sdk.payments.TransactionType;
import io.sphere.sdk.payments.commands.PaymentCreateCommand;
import io.sphere.sdk.types.CustomFieldsDraft;
import org.apache.http.HttpResponse;
import org.concordion.api.ExpectedToFail;
import org.concordion.integration.junit4.ConcordionRunner;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import specs.BaseFixture;

import javax.money.MonetaryAmount;
import javax.money.format.MonetaryFormats;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author Jan Wolter
 */
@RunWith(ConcordionRunner.class)
@ExpectedToFail
public class ChargeImmediatelyFixture extends BaseFixture {
    private static final Splitter thePaymentNamesSplitter = Splitter.on(", ");

    private static final Logger LOG = LoggerFactory.getLogger(ChargeImmediatelyFixture.class);

    public String createPayment(final String paymentName,
                                final String paymentMethod,
                                final String transactionType,
                                final String centAmount,
                                final String currencyCode) throws ExecutionException, InterruptedException {

        final MonetaryAmount monetaryAmount = createMonetaryAmountFromCent(Long.valueOf(centAmount), currencyCode);

        final List<TransactionDraft> transactions = Collections.singletonList(TransactionDraftBuilder
                .of(TransactionType.valueOf(transactionType), monetaryAmount, ZonedDateTime.now())
                .state(TransactionState.PENDING)
                .build());

        final PaymentDraft paymentDraft = PaymentDraftBuilder.of(monetaryAmount)
                .paymentMethodInfo(PaymentMethodInfoBuilder.of()
                        .method(paymentMethod)
                        .paymentInterface("PAYONE")
                        .build())
                .amountPlanned(monetaryAmount)
                .transactions(transactions)
                .custom(CustomFieldsDraft.ofTypeKeyAndObjects(
                        CustomTypeBuilder.PAYMENT_CREDIT_CARD,
                        ImmutableMap.of(
                                CustomFieldKeys.CARD_DATA_PLACEHOLDER_FIELD, getUnconfirmedVisaPseudoCardPan(),
                                CustomFieldKeys.LANGUAGE_CODE_FIELD, Locale.ENGLISH.getLanguage(),
                                CustomFieldKeys.REFERENCE_FIELD, "myGlobalKey")))
                .build();

        final BlockingClient ctpClient = ctpClient();
        final Payment payment = ctpClient.complete(PaymentCreateCommand.of(paymentDraft));
        registerPaymentWithLegibleName(paymentName, payment);

        createCartAndOrderForPayment(payment, currencyCode);

        return payment.getId();
    }

    public Map<String, String> handlePayment(final String paymentName,
                                             final String interactionTypeName,
                                             final String requestType) throws ExecutionException, IOException {
        final HttpResponse response = requestToHandlePaymentByLegibleName(paymentName);
        final Payment payment = fetchPaymentByLegibleName(paymentName);
        final String transactionId = getIdOfLastTransaction(payment);
        final String amountAuthorized = (payment.getAmountAuthorized() != null) ?
                MonetaryFormats.getAmountFormat(Locale.GERMANY).format(payment.getAmountAuthorized()) :
                BaseFixture.EMPTY_STRING;
        final String amountPaid = (payment.getAmountPaid() != null) ?
                MonetaryFormats.getAmountFormat(Locale.GERMANY).format(payment.getAmountPaid()) :
                BaseFixture.EMPTY_STRING;

        return ImmutableMap.<String, String> builder()
                .put("statusCode", Integer.toString(response.getStatusLine().getStatusCode()))
                .put("interactionCount", getInteractionCount(payment, transactionId, interactionTypeName, requestType))
                .put("transactionState", getTransactionState(payment, transactionId))
                .put("amountAuthorized", amountAuthorized)
                .put("amountPaid", amountPaid)
                .put("version", payment.getVersion().toString())
                .build();
    }

    public Map<String, String> fetchPaymentDetails(final String paymentName) throws InterruptedException, ExecutionException {

        final Payment payment = fetchPaymentByLegibleName(paymentName);

        final long appointedNotificationCount = getInteractionPaidNotificationCount(payment);

        final String transactionId = getIdOfLastTransaction(payment);
        final String amountAuthorized = (payment.getAmountAuthorized() != null) ?
                MonetaryFormats.getAmountFormat(Locale.GERMANY).format(payment.getAmountAuthorized()) :
                BaseFixture.EMPTY_STRING;

        final String amountPaid = (payment.getAmountPaid() != null) ?
                MonetaryFormats.getAmountFormat(Locale.GERMANY).format(payment.getAmountPaid()) :
                BaseFixture.EMPTY_STRING;

        return ImmutableMap.<String, String> builder()
                .put("notificationCount", Long.toString(appointedNotificationCount))
                .put("transactionState", getTransactionState(payment, transactionId))
                .put("amountAuthorized", amountAuthorized)
                .put("amountPaid", amountPaid)
                .put("version", payment.getVersion().toString())
                .build();
    }

    public boolean receivedPaidNotificationFor(final String paymentNames) throws InterruptedException, ExecutionException {
        final ImmutableList<String> paymentNamesList = ImmutableList.copyOf(thePaymentNamesSplitter.split(paymentNames));

        long remainingWaitTimeInMillis = PAYONE_NOTIFICATION_TIMEOUT;

        final long sleepDuration = 100L;

        long numberOfPaymentsWithPaidNotification = countPaymentsWithPaidNotification(paymentNamesList);
        while ((numberOfPaymentsWithPaidNotification != paymentNamesList.size()) && (remainingWaitTimeInMillis > 0L)) {
            Thread.sleep(sleepDuration);
            remainingWaitTimeInMillis -= sleepDuration;
            numberOfPaymentsWithPaidNotification = countPaymentsWithPaidNotification(paymentNamesList);
        }

        LOG.info(String.format(
                "waited %d seconds to receive notifications for payments %s",
                TimeUnit.MILLISECONDS.toSeconds(PAYONE_NOTIFICATION_TIMEOUT - remainingWaitTimeInMillis),
                Arrays.toString(paymentNamesList.stream().map(this::getIdForLegibleName).toArray())));

        return numberOfPaymentsWithPaidNotification == paymentNamesList.size();
    }

    private long countPaymentsWithPaidNotification(final ImmutableList<String> paymentNames) throws ExecutionException {
        final List<ExecutionException> exceptions = Lists.newArrayList();
        final long result = paymentNames.stream().mapToLong(paymentName -> {
            final Payment payment = fetchPaymentByLegibleName(paymentName);
            try {
                return getInteractionPaidNotificationCount(payment);
            } catch (final ExecutionException e) {
                LOG.error("Exception: %s", e);
                exceptions.add(e);
                return 0L;
            }
        }).filter(notifications -> notifications > 0L).count();

        if (!exceptions.isEmpty()) {
            throw exceptions.get(0);
        }

        return result;
    }

    private String getInteractionCount(final Payment payment,
                                       final String transactionId,
                                       final String interactionTypeName,
                                       final String requestType) throws ExecutionException {
        final String interactionTypeId = typeIdFromTypeName(interactionTypeName);
        return Long.toString(payment.getInterfaceInteractions().stream()
                .filter(i -> interactionTypeId.equals(i.getType().getId()))
                .filter(i -> transactionId.equals(i.getFieldAsString(CustomFieldKeys.TRANSACTION_ID_FIELD)))
                .filter(i -> {
                    final String requestField = i.getFieldAsString(CustomFieldKeys.REQUEST_FIELD);
                    return (requestField != null) && requestField.contains("request=" + requestType);
                })
                .count());
    }

    public long getInteractionPaidNotificationCount(final String paymentName) throws ExecutionException {
        Payment payment = fetchPaymentByLegibleName(paymentName);
        return getInteractionPaidNotificationCount(payment);
    }

    private long getInteractionPaidNotificationCount(final Payment payment) throws ExecutionException {
        final String interactionTypeId = typeIdFromTypeName(CustomTypeBuilder.PAYONE_INTERACTION_NOTIFICATION);

        return payment.getInterfaceInteractions().stream()
                .filter(i -> interactionTypeId.equals(i.getType().getId()))
                .filter(i -> "paid".equals(i.getFieldAsString(CustomFieldKeys.TX_ACTION_FIELD)))
                .filter(i -> {
                    final String notificationField = i.getFieldAsString(CustomFieldKeys.NOTIFICATION_FIELD);
                    return (notificationField != null) &&
                            (notificationField.toLowerCase().contains("transactionstatus=completed")
                                    || notificationField.toLowerCase().contains("transactionstatus=null"));
                })
                .count();
    }
}
