package com.example.backend.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * DTO לבקשה ליצירת שיחה חדשה
 * 
 * מה המשתמש שולח כשיוצר שיחה:
 * - כותרת (שם השיחה)
 * - קבצי PDF (1 או יותר)
 * 
 * דוגמה:
 * POST /api/chats
 * Content-Type: multipart/form-data
 * {
 *   "title": "ניתוח חוזה משכנתא",
 *   "files": [file1.pdf, file2.pdf]
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateChatRequest {

    /**
     * כותרת השיחה
     * 
     * @NotBlank - חייב להיות משהו (לא ריק)
     * @Size - בין 3 ל-255 תווים
     */
    @NotBlank(message = "כותרת השיחה היא שדה חובה")
    @Size(min = 3, max = 255, message = "כותרת השיחה חייבת להיות בין 3 ל-255 תווים")
    private String title;

    /**
     * קבצי PDF להעלאה
     * 
     * @NotNull - חייב להיות (לא null)
     * @Size(min = 1) - לפחות קובץ אחד!
     * 
     * למה לפחות 1?
     * כי בלי מסמכים אין על מה לשאול שאלות!
     */
    @NotNull(message = "חייב להעלות לפחות קובץ אחד")
    @Size(min = 1, message = "חייב להעלות לפחות קובץ אחד")
    private List<MultipartFile> files;

    // ==================== Validation Methods ====================

    /**
     * בדיקה נוספת - האם כל הקבצים הם PDFs?
     */
    public boolean validateFileTypes() {
        if (files == null || files.isEmpty()) {
            return false;
        }

        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
                return false;
            }
        }
        return true;
    }

    /**
     * בדיקה - האם יש קבצים ריקים?
     */
    public boolean hasEmptyFiles() {
        if (files == null) {
            return true;
        }

        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * בדיקה - האם יש קבצים גדולים מדי?
     * מקסימום: 50MB לקובץ
     */
    public boolean hasOversizedFiles() {
        if (files == null) {
            return false;
        }

        final long MAX_SIZE = 50 * 1024 * 1024; // 50MB

        for (MultipartFile file : files) {
            if (file.getSize() > MAX_SIZE) {
                return true;
            }
        }
        return false;
    }

    /**
     * סכום גודל כל הקבצים (בבתים)
     */
    public long getTotalFilesSize() {
        if (files == null || files.isEmpty()) {
            return 0;
        }

        return files.stream()
                .mapToLong(MultipartFile::getSize)
                .sum();
    }

    /**
     * כמה קבצים יש?
     */
    public int getFileCount() {
        return files != null ? files.size() : 0;
    }

    /**
     * רשימת שמות הקבצים
     */
    public List<String> getFileNames() {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        return files.stream()
                .map(MultipartFile::getOriginalFilename)
                .toList();
    }
}