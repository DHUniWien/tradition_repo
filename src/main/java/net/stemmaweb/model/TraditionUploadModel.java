package net.stemmaweb.model;

// This is a class purely for documentation of multipart/form-data uploads.

import io.swagger.v3.oas.annotations.media.Schema;

public class TraditionUploadModel {
    @Schema(description = "The name of the tradition")
    private String name;

    @Schema(description = "the language of the tradition text (e.g. Latin, Syriac)")
    private String language;

    @Schema(description = "the direction in which the text should be read. Possible values are " +
            "'LR' (left to right), 'RL' (right to left), or 'BI' (bidirectional). Default is LR.")
    private String direction;

    @Schema(description = "the ID of the user to whom this tradition belongs. Required.", required = true)
    private String userId;

    @Schema(description = "If true, the tradition will be marked as publicly viewable.")
    private boolean is_public;

    @Schema(description = "The format of the section data file. Required if 'file' is present.")
    private String filetype;

    @Schema(description = "Should be set to some non-null value if the tradition is being created without any data " +
            "file. Required if 'file' is not present.")
    private String empty;

    @Schema(description = "The tradition file data", type = "string", format = "binary")
    private String file;
}