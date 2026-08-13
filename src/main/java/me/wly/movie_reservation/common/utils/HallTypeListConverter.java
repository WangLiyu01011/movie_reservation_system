package me.wly.movie_reservation.common.utils;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import me.wly.movie_reservation.HallType;
import me.wly.movie_reservation.model.entity.Hall;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Converter
public class HallTypeListConverter implements AttributeConverter<List<HallType>,String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /**
     * 1. 实体对象转数据库字符串：List<HallType> -> ["BASIC","IMAX"]
     */
    @Override
    public String convertToDatabaseColumn(List<HallType> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            // 提取出所有枚举的 code 组成一个新的 List<String>
            List<String> codes = attribute.stream()
                    .map(HallType::getCode)
                    .toList();
            // 序列化为 JSON 数组字符串
            return MAPPER.writeValueAsString(codes);
        } catch (Exception e) {
            throw new RuntimeException("序列化 HallType 列表失败", e);
        }
    }

    /**
     * 2. 数据库字符串转实体对象：["BASIC","IMAX"] -> List<HallType>
     */
    @Override
    public List<HallType> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty() || "[]".equals(dbData)) {
            return new ArrayList<HallType>();
        }
        try {
            // 1. 先将 JSON 数组字符串解析为 List<String> 类型的 codes
            List<String> codes = MAPPER.readValue(dbData, new TypeReference<List<String>>() {});

            // 2. 遍历 codes，通过 code 匹配找回对应的 HallType 枚举对象
            return codes.stream()
                    .map(code -> Arrays.stream(HallType.values())
                            .filter(type -> type.getCode().equalsIgnoreCase(code))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("未知的 HallType Code: " + code)))
                    .toList();
        } catch (Exception e) {
            throw new RuntimeException("反序列化 HallType 列表失败", e);
        }
    }
}

