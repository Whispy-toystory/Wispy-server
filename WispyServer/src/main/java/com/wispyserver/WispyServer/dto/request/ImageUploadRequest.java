package com.wispyserver.WispyServer.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageUploadRequest {

    private MultipartFile front;
    private MultipartFile left;
    private MultipartFile right;
    private MultipartFile back;

    public boolean hasAllImages() {
        return front != null && !front.isEmpty() &&
                left != null && !left.isEmpty() &&
                right != null && !right.isEmpty() &&
                back != null && !back.isEmpty();
    }

}
