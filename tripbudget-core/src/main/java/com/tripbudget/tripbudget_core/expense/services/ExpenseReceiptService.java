package com.tripbudget.tripbudget_core.expense.services;

import com.tripbudget.tripbudget_core.expense.entities.ExpenseEntity;
import com.tripbudget.tripbudget_core.uploads.enums.UploadPurpose;
import com.tripbudget.tripbudget_core.uploads.services.UploadAttachmentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.UUID;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ExpenseReceiptService {
    private final UploadAttachmentService uploads;

    public ExpenseReceiptService(UploadAttachmentService uploads) {
        this.uploads = uploads;
    }

    public void updateReceipt(Long actorId, ExpenseEntity expense, UUID uploadId,
                              boolean removeReceipt, String legacyUrl) {
        if ((uploadId != null && removeReceipt)
                || ((uploadId != null || removeReceipt) && legacyUrl != null && !legacyUrl.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conflicting receipt changes");
        }
        if (uploadId == null && !removeReceipt && legacyUrl == null) return; // do not change image
        if (uploadId != null && uploadId.equals(expense.getReceiptUploadId())) return;

        UUID previous = expense.getReceiptUploadId();
        String url;
        if (uploadId != null) {
            url = validateUrl(uploads.attachPending(actorId, uploadId, UploadPurpose.EXPENSE_RECEIPT));
        } else {
            url = removeReceipt ? null : validateUrl(legacyUrl);
            if (!removeReceipt && java.util.Objects.equals(url, expense.getReceiptUrl())) return;
        }
        expense.updateReceipt(uploadId, url);
        if (previous != null) {
            uploads.releaseAttached(previous, UploadPurpose.EXPENSE_RECEIPT);
        }
    }

    private String validateUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String url = value.trim();
        try {
            URI uri = URI.create(url);
            if (url.length() > 500 || uri.getHost() == null
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid receipt URL");
        }
        return url;
    }
}
