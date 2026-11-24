package com.example.hibernatelab.g14_lead.i4_fixed;

/**
 * Kết quả của một lần import: bao nhiêu dòng, và persistence context từng phình tới cỡ nào.
 * {@code peakManagedEntities} là bằng chứng chẩn đoán: càng lớn thì mỗi lần flush càng phải dirty-check nhiều entity.
 */
public record I4FixedImportReport(int rows, int peakManagedEntities) {
}
