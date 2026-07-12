package com.subhajit.aiassistant.Configures;

import com.fasterxml.jackson.annotation.JsonAlias;

public record ToolRequest(
        @JsonAlias({"query", "expression", "value"}) String payload
) {}
