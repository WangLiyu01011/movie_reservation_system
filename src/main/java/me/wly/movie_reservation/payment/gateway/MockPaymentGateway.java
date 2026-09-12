package me.wly.movie_reservation.payment.gateway;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import me.wly.movie_reservation.common.exception.BusinessException;
import me.wly.movie_reservation.common.exception.ResultCode;
import me.wly.movie_reservation.common.util.UuidCodeGenerator;
import me.wly.movie_reservation.payment.dto.PaymentCallbackDTO;
import me.wly.movie_reservation.payment.model.PaymentCallbackStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MockPaymentGateway implements PaymentGateway {
    private static final String CHANNEL = "MOCK";

    private final String checkoutBaseUrl;
    private final String callbackSecret;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public MockPaymentGateway(
            @Value("${payment.mock.checkout-base-url:http://localhost:8080/api/v1/mock-payments}")
            String checkoutBaseUrl,
            @Value("${payment.mock.callback-secret}") String callbackSecret,
            ObjectMapper objectMapper,
            Validator validator
    ) {
        this.checkoutBaseUrl = checkoutBaseUrl;
        this.callbackSecret = callbackSecret;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public PaymentCreateResult createPayment(PaymentCreateCommand command) {
        String providerTradeNo = UuidCodeGenerator.generateCode("mock_trade_");
        String payUrl = UriComponentsBuilder.fromUriString(checkoutBaseUrl)
                .pathSegment(command.paymentNo())
                .build()
                .toUriString();

        return new PaymentCreateResult(
                providerTradeNo,
                payUrl,
                command.expiresAt()
        );
    }

    @Override
    public VerifiedPaymentCallback verifyAndParseCallback(String rawBody, String signature) {
        verifySignature(rawBody, signature);

        PaymentCallbackDTO dto;
        try {
            dto = objectMapper.readValue(rawBody, PaymentCallbackDTO.class);
        } catch (JacksonException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Invalid payment callback body");
        }

        Set<ConstraintViolation<PaymentCallbackDTO>> violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new BusinessException(ResultCode.BAD_REQUEST, "Invalid payment callback: " + message);
        }
        if (dto.status() == PaymentCallbackStatus.SUCCESS
                && dto.paidAt() == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "paidAt is required for a successful callback");
        }

        return new VerifiedPaymentCallback(
                dto.eventId(),
                CHANNEL,
                dto.paymentNo(),
                dto.providerTradeNo(),
                dto.amount(),
                dto.status(),
                dto.paidAt(),
                dto.failureCode(),
                dto.failureMessage(),
                rawBody
        );
    }

    private void verifySignature(String rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Missing payment callback signature");
        }

        byte[] expected = calculateSignature(rawBody).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.trim().toLowerCase().getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Invalid payment callback signature");
        }
    }

    private String calculateSignature(String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(callbackSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot calculate mock callback signature", exception);
        }
    }
}
