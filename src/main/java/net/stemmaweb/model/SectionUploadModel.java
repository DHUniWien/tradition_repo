package net.stemmaweb.model;

// This is a class purely for documentation of multipart/form-data uploads.

import io.swagger.v3.oas.annotations.media.Schema;

public class SectionUploadModel {
    @Schema(description = "The name of the section")
    private String name;

    @Schema(description = "The format of the section data file")
    private String filetype;

    @Schema(description = "The section file data", type = "string", format = "binary")
    private String file;
}