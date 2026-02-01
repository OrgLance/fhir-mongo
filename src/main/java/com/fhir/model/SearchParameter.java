package com.fhir.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchParameter {

    private String name;
    private String value;
    private String modifier;
    private String prefix;
}
