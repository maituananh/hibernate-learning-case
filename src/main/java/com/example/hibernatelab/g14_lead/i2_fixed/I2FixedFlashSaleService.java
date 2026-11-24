package com.example.hibernatelab.g14_lead.i2_fixed;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Use case "mua hàng trong flash sale": BẢN ĐÃ SỬA.
 *
 * BIẾN THỂ : I2 fixed
 * BASELINE : I2 buggy
 * KHÁC BIỆT: entity có @Version; service bắt {@link OptimisticLockingFailureException} và chạy lại CẢ transaction (retry)
 * HẬU QUẢ  : người đến sau bị từ chối ở bước ghi, retry đọc lại stock mới nên bị chặn đúng bằng "hết hàng"
 */
@Service
public class I2FixedFlashSaleService {

	/** Số lần thử tối đa. Hết lượt vẫn xung đột thì ném lỗi cho caller (quá nhiều người tranh một dòng). */
	private static final int MAX_ATTEMPTS = 3;

	private final I2FixedProductRepository products;
	private final I2FixedSaleRepository sales;
	private final TransactionTemplate tx;

	public I2FixedFlashSaleService(I2FixedProductRepository products, I2FixedSaleRepository sales,
		PlatformTransactionManager transactionManager) {
		this.products = products;
		this.sales = sales;
		this.tx = new TransactionTemplate(transactionManager);
	}

	public void buy(Long productId, int quantity) {
		buy(productId, quantity, () -> { });
	}

	/** {@code afterStockRead} chỉ để TEST ép thứ tự xen kẽ; chỉ áp dụng cho lần thử đầu tiên. */
	public void buy(Long productId, int quantity, Runnable afterStockRead) {
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			Runnable hook = (attempt == 1) ? afterStockRead : () -> { };
			try {
				// Retry phải bao NGOÀI transaction: transaction đã lỗi version thì rollback-only, không thể "thử tiếp" bên trong nó.
				tx.executeWithoutResult(status -> buyOnce(productId, quantity, hook));
				return;
			} catch (OptimisticLockingFailureException conflict) {
				if (attempt == MAX_ATTEMPTS) {
					throw conflict;
				}
			}
		}
	}

	private void buyOnce(Long productId, int quantity, Runnable afterStockRead) {
		I2FixedProduct product = products.findById(productId).orElseThrow();
		afterStockRead.run();
		if (product.getStock() < quantity) {
			throw new I2FixedInsufficientStockException(product.getStock(), quantity);
		}
		product.decreaseStock(quantity); // [FLUSH] lúc commit: update … where id=? and version=?
		sales.save(new I2FixedSale(productId, quantity));
	}
}
