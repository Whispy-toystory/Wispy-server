package com.wispyserver.WispyServer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterSelectResponse {

    private UserInfo user;

    private CharacterInfo character;

    private SummaryInfo summary;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserInfo {
        private String name;
        private String birthDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CharacterInfo {
        private String characterId;
        private String name;
        private String glbUrl;
        private Boolean isReady;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SummaryInfo {
        private String date;
        private String text;
    }
}