package net.stemmaweb.model;

// This is a class purely for documentation of multipart/form-data uploads.

import io.swagger.v3.oas.annotations.media.Schema;

public class TraditionUploadModel {
    /*
     * @param name      the name of the tradition. Default is the empty string.
     * @param language  the language of the tradition text (e.g. Latin, Syriac).
     * @param direction the direction in which the text should be read. Possible values
     *                  are {@code LR} (left to right), {@code RL} (right to left), or {@code BI} (bidirectional).
     *                  Default is LR.
     * @param userId    the ID of the user to whom this tradition belongs. Required.
     * @param is_public If true, the tradition will be marked as publicly viewable.
     * @param filetype  the type of file being uploaded. Possible values are {@code collatex},
     *                  {@code cxjson}, {@code csv}, {@code tsv}, {@code xls}, {@code xlsx},
     *                  {@code graphml}, {@code stemmaweb}, or {@code teips}.
     *                  Required if 'file' is present.
     * @param empty     Should be set to some non-null value if the tradition is being created without any data file.
     *                  Required if 'file' is not present.
     * @param uploadedInputStream The file data to upload.
     * @param fileDetail The file data to upload.
     */
    @Schema(description = "The name of the section")
    private String name;

    @Schema(description = "the language of the tradition text (e.g. Latin, Syriac)")
    private String language;

    @Schema(description = "the direction in which the text should be read. Possible values are " +
            "'LR' (left to right), 'RL' (right to left), or 'BI' (bidirectional). Default is LR.")
    private String direction;

    @Schema(description = "the ID of the user to whom this tradition belongs. Required.")
    private String userId;

    @Schema(description = "If true, the tradition will be marked as publicly viewable.")
    private boolean is_public;

    @Schema(description = "The format of the section data file. Required if 'file' is present.")
    private String filetype;

    @Schema(description = "Should be set to some non-null value if the tradition is being created without any data " +
            "file. Required if 'file' is not present.")
    private String empty;

    @Schema(description = "The section file data", type = "string", format = "binary")
    private String file;
}
