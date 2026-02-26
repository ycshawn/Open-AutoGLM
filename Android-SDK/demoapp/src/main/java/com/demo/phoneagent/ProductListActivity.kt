package com.demo.phoneagent

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView
import com.demo.phoneagent.databinding.ActivityProductListBinding
import com.demo.phoneagent.databinding.ItemProductBinding
import com.google.android.material.chip.Chip

/**
 * Product list activity for testing the agent.
 * Displays a grid of products that can be searched and filtered.
 */
class ProductListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductListBinding
    private lateinit var adapter: ProductAdapter
    private var allProducts = Product.getAllProducts()
    private var filteredProducts = allProducts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Setup RecyclerView
        adapter = ProductAdapter { product ->
            onProductClick(product)
        }
        binding.rvProducts.adapter = adapter
        adapter.submitList(filteredProducts)

        // Setup search
        binding.etSearch.doAfterTextChanged { text ->
            filterProducts(text?.toString() ?: "")
        }

        // Setup category chips
        binding.chipAll.isChecked = true
        binding.chipAll.setOnClickListener {
            filterByCategory(null)
        }
        binding.chipFruits.setOnClickListener {
            filterByCategory("水果")
        }
        binding.chipDrinks.setOnClickListener {
            filterByCategory("饮品")
        }
        binding.chipSnacks.setOnClickListener {
            filterByCategory("零食")
        }

        // Clear button
        binding.ivClear.setOnClickListener {
            binding.etSearch.text?.clear()
        }
    }

    private fun filterProducts(query: String) {
        binding.ivClear.visibility = if (query.isNotEmpty()) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }

        val selectedCategory = when (binding.chipGroup.checkedChipId) {
            R.id.chipFruits -> "水果"
            R.id.chipDrinks -> "饮品"
            R.id.chipSnacks -> "零食"
            else -> null
        }

        filteredProducts = if (query.isEmpty() && selectedCategory == null) {
            allProducts
        } else {
            allProducts.filter { product ->
                val matchesQuery = query.isEmpty() || product.name.contains(query, ignoreCase = true)
                val matchesCategory = selectedCategory == null || product.category == selectedCategory
                matchesQuery && matchesCategory
            }
        }

        adapter.submitList(filteredProducts)
    }

    private fun filterByCategory(category: String?) {
        val query = binding.etSearch.text?.toString() ?: ""
        filterProducts(query)
    }

    private fun onProductClick(product: Product) {
        ProductDetailActivity.start(this, product)
    }
}

/**
 * Data class representing a product.
 */
data class Product(
    val id: String,
    val name: String,
    val price: Double,
    val category: String,
    val emoji: String
) : java.io.Serializable {
    companion object {
        fun getAllProducts(): List<Product> = listOf(
            Product("1", "新鲜苹果", 12.8, "水果", "🍎"),
            Product("2", "香蕉", 8.5, "水果", "🍌"),
            Product("3", "橙子", 15.0, "水果", "🍊"),
            Product("4", "草莓", 25.0, "水果", "🍓"),
            Product("5", "葡萄", 18.0, "水果", "🍇"),
            Product("6", "西瓜", 6.0, "水果", "🍉"),
            Product("7", "牛奶", 25.0, "饮品", "🥛"),
            Product("8", "咖啡", 35.0, "饮品", "☕"),
            Product("9", "茶", 20.0, "饮品", "🍵"),
            Product("10", "果汁", 15.0, "饮品", "🧃"),
            Product("11", "薯片", 12.0, "零食", "🥔"),
            Product("12", "饼干", 8.0, "零食", "🍪"),
            Product("13", "巧克力", 15.0, "零食", "🍫"),
            Product("14", "糖果", 5.0, "零食", "🍬")
        )
    }
}

/**
 * RecyclerView adapter for products.
 */
class ProductAdapter(
    private val onProductClick: (Product) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    private var products = listOf<Product>()

    fun submitList(newProducts: List<Product>) {
        products = newProducts
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount(): Int = products.size

    inner class ProductViewHolder(
        private val binding: ItemProductBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            binding.tvProductEmoji.text = product.emoji
            binding.tvProductName.text = product.name
            binding.tvProductCategory.text = product.category
            binding.tvProductPrice.text = "¥${product.price}"

            binding.cardProduct.setOnClickListener {
                onProductClick(product)
            }
        }
    }
}

/**
 * Product detail activity (placeholder).
 */
class ProductDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PRODUCT = "product"

        fun start(activity: AppCompatActivity, product: Product) {
            val intent = Intent(activity, ProductDetailActivity::class.java).apply {
                putExtra(EXTRA_PRODUCT, product)
            }
            activity.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get product from intent
        @Suppress("DEPRECATION")
        val product = intent.getSerializableExtra(EXTRA_PRODUCT) as? Product

        // Create simple detail view
        val detailView = TextView(this).apply {
            text = buildString {
                appendLine("🛍️ 商品详情\n")
                appendLine()
                if (product != null) {
                    appendLine("名称: ${product.name}")
                    appendLine("分类: ${product.category}")
                    appendLine("价格: ¥${product.price}")
                } else {
                    appendLine("商品信息加载失败")
                }
            }
            setPadding(64, 64, 64, 64)
            textSize = 18f
        }

        setContentView(detailView)
        setTitle(product?.name ?: "商品详情")

        // Enable back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
