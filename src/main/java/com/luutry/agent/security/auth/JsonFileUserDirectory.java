package com.luutry.agent.security.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

/**
 * 基于 JSON 文件的用户目录实现。
 * <p>
 * 从 mock/user_directory.json 加载用户权限映射，替代硬编码的 MockUserDirectory。
 * 修改 JSON 文件即可调整用户权限，无需改 Java 代码。
 * </p>
 */
@Slf4j
@Component
public class JsonFileUserDirectory implements UserDirectory {

    private static final String USER_DIRECTORY_FILE = "mock/user_directory.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Map<String, Map<String, Object>> userMap;

    public JsonFileUserDirectory() {
        this.userMap = loadUserDirectory();
    }

    @Override
    public UserContext resolve(String userId) {
        if (userId == null || userId.isBlank()) {
            return UserContext.builder().userId("").build();
        }
        Map<String, Object> entry = userMap.get(userId);
        if (entry == null) {
            // 未识别用户：空范围、无权限，默认拒绝
            return UserContext.builder()
                    .userId(userId)
                    .role("unknown")
                    .build();
        }

        @SuppressWarnings("unchecked")
        List<String> managedOrgIds = (List<String>) entry.getOrDefault("managedOrgIds", Collections.emptyList());
        @SuppressWarnings("unchecked")
        Set<String> permissions = new HashSet<>((List<String>) entry.getOrDefault("permissions", Collections.emptyList()));

        return UserContext.builder()
                .userId(userId)
                .role(String.valueOf(entry.getOrDefault("role", "unknown")))
                .managedOrgIds(managedOrgIds)
                .permissions(permissions)
                .build();
    }

    private Map<String, Map<String, Object>> loadUserDirectory() {
        try (InputStream is = new ClassPathResource(USER_DIRECTORY_FILE).getInputStream()) {
            List<Map<String, Object>> users = OBJECT_MAPPER.readValue(is, new TypeReference<>() {});
            Map<String, Map<String, Object>> map = new LinkedHashMap<>();
            for (Map<String, Object> user : users) {
                String uid = String.valueOf(user.get("userId"));
                map.put(uid, user);
            }
            return map;
        } catch (Exception e) {
            log.error("Failed to load user directory from {}: {}", USER_DIRECTORY_FILE, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
