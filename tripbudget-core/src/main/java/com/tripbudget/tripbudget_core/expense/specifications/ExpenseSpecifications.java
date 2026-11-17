package com.tripbudget.tripbudget_core.expense.specifications;

import com.tripbudget.tripbudget_core.expense.dtos.request.ExpenseFilterRequest;
import com.tripbudget.tripbudget_core.expense.entities.ExpenseEntity;
import com.tripbudget.tripbudget_core.expense.enums.ExpenseCategory;
import com.tripbudget.tripbudget_core.expense.enums.ExpenseStatus;
import com.tripbudget.tripbudget_core.expense.enums.SplitType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ExpenseSpecifications {

    private ExpenseSpecifications() {}

    public static Specification<ExpenseEntity> buildSpecification(
            Long tripId,
            ExpenseFilterRequest filter,
            Collection<Long> matchedPayerIds,
            boolean hasPayerFilter
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.equal(root.get("tripId"), tripId));
            predicates.add(cb.notEqual(root.get("status"), ExpenseStatus.DELETED));
            predicates.add(cb.isFalse(root.get("isDel")));

            if (filter != null) {
                if (filter.search() != null && !filter.search().isBlank()) {
                    String pattern = "%" + filter.search().trim().toLowerCase() + "%";
                    predicates.add(cb.like(cb.lower(root.get("title")), pattern));
                }

                if (filter.title() != null && !filter.title().isBlank()) {
                    String titlePattern = "%" + filter.title().trim().toLowerCase() + "%";
                    predicates.add(cb.like(cb.lower(root.get("title")), titlePattern));
                }

                if (filter.category() != null && !filter.category().isBlank()) {
                    List<ExpenseCategory> categories = Arrays.stream(filter.category().split(","))
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .map(s -> {
                                try {
                                    return ExpenseCategory.valueOf(s.toUpperCase(Locale.ROOT));
                                } catch (IllegalArgumentException e) {
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .toList();
                    if (!categories.isEmpty()) {
                        predicates.add(root.get("category").in(categories));
                    }
                }

                if (filter.splitType() != null && !filter.splitType().isBlank()) {
                    List<SplitType> splitTypes = Arrays.stream(filter.splitType().split(","))
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .map(s -> {
                                try {
                                    return SplitType.valueOf(s.toUpperCase(Locale.ROOT));
                                } catch (IllegalArgumentException e) {
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .toList();
                    if (!splitTypes.isEmpty()) {
                        predicates.add(root.get("splitType").in(splitTypes));
                    }
                }

                if (hasPayerFilter) {
                    if (matchedPayerIds == null || matchedPayerIds.isEmpty()) {
                        predicates.add(cb.disjunction());
                    } else {
                        predicates.add(root.get("payerId").in(matchedPayerIds));
                    }
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Sort buildSort(String sortBy, String sortDirection) {
        String property = switch (sortBy == null ? "" : sortBy.trim().toLowerCase()) {
            case "title" -> "title";
            case "amount" -> "amount";
            case "category" -> "category";
            case "splittype" -> "splitType";
            case "createdat" -> "createdAt";
            case "id" -> "id";
            case "expensetime", "date" -> "expenseTime";
            default -> "expenseTime";
        };

        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        if ("expenseTime".equals(property)) {
            return Sort.by(direction, "expenseTime").and(Sort.by(Sort.Direction.DESC, "id"));
        }

        return Sort.by(direction, property);
    }
}

