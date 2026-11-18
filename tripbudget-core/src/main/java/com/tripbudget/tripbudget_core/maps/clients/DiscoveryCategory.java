package com.tripbudget.tripbudget_core.maps.clients;

import com.tripbudget.tripbudget_core.maps.web.MapsException;
import tools.jackson.databind.JsonNode;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Conservative Vietmap POI groups, verified against published examples and v4 responses.
 * Each request uses one category code; do not concatenate codes into free text.
 */
public enum DiscoveryCategory {
    FOOD_BEVERAGE(List.of(new Topic("1002", "nhà hàng"), new Topic("1005", "quán ăn"), new Topic("1001", "cà phê"))),
    SHOPPING(List.of(new Topic("3001", "trung tâm thương mại"), new Topic("3004-4", "thời trang"), new Topic("3002", "siêu thị"))),
    VISIT(List.of(new Topic("4004-1", "di tích"), new Topic("4001-5", "bảo tàng"),
            new Topic("14001", "chùa"), new Topic("3004-27", "vườn thú"))),
    TRANSPORTATION(List.of(new Topic("11009", "bến xe"), new Topic("11010", "ga"),
            new Topic("11005", "bến tàu"))),
    ACCOMMODATION(List.of(new Topic("2001", "khách sạn"), new Topic("2002", "homestay"))),
    SIGHTSEEING(List.of(new Topic("4002-2", "công viên"), new Topic("4002-5", "rạp chiếu phim"),
            new Topic("4004-5", "khu du lịch"))),
    OTHER(List.of());

    public record Topic(String code, String defaultQuery) {}

    private final List<Topic> topics;

    DiscoveryCategory(List<Topic> topics) {
        this.topics = topics;
    }

    public static DiscoveryCategory parse(String value) {
        try {
            return valueOf(value == null ? "" : value);
        } catch (IllegalArgumentException exception) {
            throw MapsException.invalid("Invalid activity discovery category.");
        }
    }

    public List<Topic> topics() {
        return topics;
    }

    public List<String> searchTokens(String input) {
        String text = " " + normalize(input) + " ";
        // Category descriptions need not appear in a brand name (e.g. "khách sạn Nikko").
        List<String> descriptions = switch (this) {
            case ACCOMMODATION -> List.of("khach san", "cho o", "hotel", "homestay", "resort", "nha nghi");
            case SHOPPING -> List.of("trung tam thuong mai", "cua hang quan ao", "cua hang thoi trang",
                    "cua hang", "quan ao", "thoi trang", "sieu thi", "mua sam", "mall");
            case FOOD_BEVERAGE -> List.of("nha hang", "quan an", "ca phe", "restaurant", "cafe", "coffee");
            case VISIT -> List.of("dia diem tham quan", "di tich", "bao tang", "museum");
            case TRANSPORTATION -> List.of("ben xe", "nha ga", "ben tau");
            case SIGHTSEEING -> List.of("rap chieu phim", "khu du lich", "cong vien");
            case OTHER -> List.of();
        };
        for (String description : descriptions) {
            text = text.replace(" " + description + " ", " ");
        }
        return text.isBlank() ? List.of() : List.of(text.trim().split("\\s+"));
    }

    public boolean accepts(JsonNode place, Topic topic) {
        String name = normalize(place.path("name").asText(""));
        if (name.isBlank()) return false;
        if (this == OTHER) return true;
        // Fail closed when the provider omits or returns a different classification.
        boolean matches = false;
        for (JsonNode code : place.path("categories")) {
            if (code.asText().equals(topic.code()) || code.asText().startsWith(topic.code() + "-")) {
                matches = true;
            }
        }
        if (!matches) return false;
        // Some v4 records have incorrect metadata. Reject obvious cross-category results.
        if (containsAny(name, "spa", "beauty", "benh vien", "phong kham", "nha khoa", "thu cung", "cattery")) return false;
        if (this != FOOD_BEVERAGE && this != SHOPPING && containsAny(name,
                "nha hang", "ca phe", "cafe", "coffee", "cocktail", "whisky")) return false;
        if (this == FOOD_BEVERAGE && containsAny(name, "khach san") && !containsAny(name, "nha hang")) return false;
        if (this == VISIT && topic.code().equals("3004-27")) {
            return containsAny(name, "vuon thu", "bach thu", "thao cam vien");
        }
        return true;
    }

    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if ((" " + text + " ").contains(" " + term + " ")) return true;
        }
        return false;
    }
}
