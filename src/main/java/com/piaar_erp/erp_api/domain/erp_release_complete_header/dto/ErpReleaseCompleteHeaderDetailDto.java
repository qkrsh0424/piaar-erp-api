package com.piaar_erp.erp_api.domain.erp_release_complete_header.dto;

import java.util.List;

import org.hibernate.annotations.Type;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ErpReleaseCompleteHeaderDetailDto {
    @Type(type = "jsonb")
    private List<DetailDto> details;
}