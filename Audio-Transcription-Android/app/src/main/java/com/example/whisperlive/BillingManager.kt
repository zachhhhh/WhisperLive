package com.example.whisperlive

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class PurchaseState {
    NotPurchased, Loading, Purchased, Error
}

class BillingManager(private val context: Context) : PurchasesUpdatedListener {
    private val billingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()
    private var isServiceConnected = false

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Loading)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState

    private val _productDetails = MutableStateFlow<List<ProductDetails>>(emptyList())
    val productDetails: StateFlow<List<ProductDetails>> = _productDetails

    companion object {
        const val PREMIUM_PRODUCT_ID = "premium_remove_ads"
    }

    init {
        startConnection()
    }

    private fun startConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isServiceConnected = true
                    queryProductDetails()
                    queryPurchases()
                } else {
                    _purchaseState.value = PurchaseState.Error
                }
            }

            override fun onBillingServiceDisconnected() {
                isServiceConnected = false
                _purchaseState.value = PurchaseState.Loading
                startConnection()
            }
        })
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = productDetailsList
            } else {
                _productDetails.value = emptyList()
                _purchaseState.value = PurchaseState.Error
            }
        }
    }

    private fun queryPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                _purchaseState.value = PurchaseState.Error
                return@queryPurchasesAsync
            }

            val premiumPurchase = purchases.firstOrNull { purchase ->
                purchase.products.contains(PREMIUM_PRODUCT_ID)
            }

            if (premiumPurchase != null) {
                handlePurchase(premiumPurchase)
            } else {
                _purchaseState.value = PurchaseState.NotPurchased
            }
        }
    }

    fun launchBillingFlow(activity: Activity, productDetails: ProductDetails) {
        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .build()
        )
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()
    
        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Handle cancel
        } else {
            _purchaseState.value = PurchaseState.Error
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (!purchase.products.contains(PREMIUM_PRODUCT_ID)) {
            return
        }

        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            // Verify purchase if needed
            if (!purchase.isAcknowledged) {
                val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(acknowledgeParams) { billingResult ->
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        _purchaseState.value = PurchaseState.Purchased
                    } else {
                        _purchaseState.value = PurchaseState.Error
                    }
                }
            } else {
                _purchaseState.value = PurchaseState.Purchased
            }
        } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            _purchaseState.value = PurchaseState.Loading
        }
    }

    fun isPremium(): Boolean = _purchaseState.value == PurchaseState.Purchased

    fun destroy() {
        if (isServiceConnected) {
            billingClient.endConnection()
            isServiceConnected = false
        }
    }
}
