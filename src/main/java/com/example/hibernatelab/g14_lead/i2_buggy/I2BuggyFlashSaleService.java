package com.example.hibernatelab.g14_lead.i2_buggy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Use case "mua hàng trong flash sale": BẢN CÓ LỖI.
 *
 * BIẾN THỂ : I2 buggy
 * BASELINE : không có (G14 được miễn quy tắc một khác biệt, E7)
 * KHÁC BIỆT: đọc stock → kiểm tra trong Java → trừ → ghi, không có khoá và entity không có @Version
 * HẬU QUẢ  : hai khách mua cùng lúc cùng thấy stock cũ, cùng qua kiểm tra "đủ hàng"; UPDATE của người sau đè người trước.
 *            Kết quả: bán nhiều hơn số hàng có, và tồn kho ghi trong DB sai. Không có exception nào.
 */
@Service
public class I2BuggyFlashSaleService {

	private final I2BuggyProductRepository products;
	private final I2BuggySaleRepository sales;
	private final TransactionTemplate tx;

	public I2BuggyFlashSaleService(I2BuggyProductRepository products, I2BuggySaleRepository sales,
		PlatformTransactionManager transactionManager) {
		this.products = products;
		this.sales = sales;
		this.tx = new TransactionTemplate(transactionManager);
	}

	public void buy(Long productId, int quantity) {
		buy(productId, quantity, () -> { });
	}

	/**
	 * {@code afterStockRead} chỉ để TEST ép thứ tự xen kẽ giữa hai transaction (cổng đồng bộ, không dùng sleep).
	 * Code thật không có tham số này; nó chạy ngay sau khi đã đọc stock và trước khi kiểm tra.
	 */
	public void buy(Long productId, int quantity, Runnable afterStockRead) {
		tx.executeWithoutResult(status -> {
			I2BuggyProduct product = products.findById(productId).orElseThrow(); // MANAGED, stock đọc vào bộ nhớ
			afterStockRead.run();
			if (product.getStock() < quantity) { // kiểm tra trên số liệu có thể đã CŨ
				throw new I2BuggyInsufficientStockException(product.getStock(), quantity);
			}
			product.decreaseStock(quantity); // [FLUSH] lúc commit: update product set stock = <số tính từ số liệu cũ>
			sales.save(new I2BuggySale(productId, quantity));
		});
	}
}
