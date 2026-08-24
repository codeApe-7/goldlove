package com.love.archive.admin.application;

import com.love.archive.admin.persistence.AdminActivationCodeQueryMapper;
import com.love.archive.admin.persistence.AdminPaymentOrderQueryMapper;
import com.love.archive.common.web.PageView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理员查看所有支付订单与激活码。写操作在各自的领域模块里。 */
@Service
@RequiredArgsConstructor
public class AdminLedgerQueryService {

    private static final long MAX_PAGE_SIZE = 100;

    private final AdminPaymentOrderQueryMapper paymentOrderQueryMapper;
    private final AdminActivationCodeQueryMapper activationCodeQueryMapper;

    @Transactional(readOnly = true)
    public PageView<AdminPaymentOrderItem> listOrders(
            String phone, String status, long requestedPage, long requestedSize) {
        long pageNumber = Math.max(1, requestedPage);
        long pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, requestedSize));
        String phoneLike = like(phone);
        String normalizedStatus = blankToNull(status);
        List<AdminPaymentOrderItem> items = paymentOrderQueryMapper
                .search(phoneLike, normalizedStatus, pageSize, (pageNumber - 1) * pageSize)
                .stream()
                .map(row -> new AdminPaymentOrderItem(
                        row.getId(),
                        row.getOutTradeNo(),
                        row.getPhone(),
                        row.getChannel(),
                        row.getAmountMinor() == null ? 0L : row.getAmountMinor(),
                        row.getStatus(),
                        row.getChannelTradeNo(),
                        row.getPaidAt(),
                        row.getCreatedAt()))
                .toList();
        return new PageView<>(
                items, pageNumber, pageSize,
                paymentOrderQueryMapper.count(phoneLike, normalizedStatus));
    }

    @Transactional(readOnly = true)
    public PageView<AdminActivationCodeItem> listActivationCodes(
            String phone, String status, long requestedPage, long requestedSize) {
        long pageNumber = Math.max(1, requestedPage);
        long pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, requestedSize));
        String phoneLike = like(phone);
        String normalizedStatus = blankToNull(status);
        List<AdminActivationCodeItem> items = activationCodeQueryMapper
                .search(phoneLike, normalizedStatus, pageSize, (pageNumber - 1) * pageSize)
                .stream()
                .map(row -> new AdminActivationCodeItem(
                        row.getId(),
                        row.getCode(),
                        row.getBoundPhone(),
                        Boolean.TRUE.equals(row.getBoundPhoneRegistered()),
                        row.getGrantedTier(),
                        row.getStatus(),
                        row.getNote(),
                        row.getCreatedAt(),
                        row.getRedeemedAt(),
                        row.getRedeemedPhone()))
                .toList();
        return new PageView<>(
                items, pageNumber, pageSize,
                activationCodeQueryMapper.count(phoneLike, normalizedStatus));
    }

    private static String like(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : "%" + normalized + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
