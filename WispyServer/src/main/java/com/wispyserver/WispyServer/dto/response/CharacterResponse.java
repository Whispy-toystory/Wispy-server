package com.wispyserver.WispyServer.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterResponse {

    private UUID characterId;

    private String characterName;

    private Integer characterSlot;

    private String glbUrl;

    private LocalDate createdDate;

}
