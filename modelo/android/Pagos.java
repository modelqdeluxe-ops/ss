package hn.hato.ganadero;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.android.billingclient.api.AccountIdentifiers;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import org.json.JSONObject;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* Puente "Pagos" de Google Play Billing (7.x) para la WebView. Lo registra Enlace si la clase está en la app:
   la app del dueño para Google Play se arma con Gradle (scripts/pagos.sh), que trae la biblioteca y sus dependencias.
   JS -> iniciar(producto), comprar(producto, cuenta, licencia), consumir(token), verificar(llave, json, firma)
   Java -> window.pagosEvento({tipo:'precio'|'compra'|'consumida'|'error', ...})
   La licencia viaja dentro de la compra (obfuscatedProfileId): así la compra de Google queda atada a esa licencia. */
public class Pagos implements PurchasesUpdatedListener {
    private final Activity act;
    private final WebView web;
    private BillingClient bc;
    private final Map<String, ProductDetails> detalles = new HashMap<String, ProductDetails>();

    public Pagos(Activity a, WebView w) {
        act = a;
        web = w;
    }

    private void evento(final JSONObject o) {
        final String js = "window.pagosEvento&&pagosEvento(" + JSONObject.quote(o.toString()) + ")";
        act.runOnUiThread(new Runnable() {
            public void run() {
                try { web.evaluateJavascript(js, null); } catch (Throwable e) { }
            }
        });
    }

    private void error(String codigo, String mensaje) {
        try {
            JSONObject o = new JSONObject();
            o.put("tipo", "error");
            o.put("codigo", codigo);
            if (mensaje != null) o.put("mensaje", mensaje);
            evento(o);
        } catch (Throwable e) { }
    }

    private void conectar(final Runnable luego) {
        if (bc != null && bc.isReady()) { luego.run(); return; }
        if (bc == null) {
            bc = BillingClient.newBuilder(act).setListener(this)
                    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()).build();
        }
        bc.startConnection(new BillingClientStateListener() {
            public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) luego.run();
                else error("conexion", "Google Play no está disponible en este teléfono.");
            }
            public void onBillingServiceDisconnected() { }
        });
    }

    private void consultar(final String producto, final Runnable luego) {
        QueryProductDetailsParams p = QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(
                QueryProductDetailsParams.Product.newBuilder().setProductId(producto).setProductType(BillingClient.ProductType.INAPP).build())).build();
        bc.queryProductDetailsAsync(p, new ProductDetailsResponseListener() {
            public void onProductDetailsResponse(BillingResult r, List<ProductDetails> lista) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK && lista != null) {
                    for (ProductDetails d : lista) {
                        detalles.put(d.getProductId(), d);
                        try {
                            JSONObject o = new JSONObject();
                            o.put("tipo", "precio");
                            o.put("producto", d.getProductId());
                            ProductDetails.OneTimePurchaseOfferDetails of = d.getOneTimePurchaseOfferDetails();
                            if (of != null) o.put("precio", of.getFormattedPrice());
                            evento(o);
                        } catch (Throwable e) { }
                    }
                }
                if (luego != null) luego.run();
            }
        });
    }

    private void avisarCompra(Purchase c) {
        try {
            JSONObject o = new JSONObject();
            o.put("tipo", "compra");
            List<String> pr = c.getProducts();
            o.put("producto", pr != null && !pr.isEmpty() ? pr.get(0) : "");
            o.put("estado", c.getPurchaseState() == Purchase.PurchaseState.PURCHASED ? "comprada" : "pendiente");
            o.put("token", c.getPurchaseToken());
            o.put("orden", c.getOrderId() == null ? "" : c.getOrderId());
            o.put("json", c.getOriginalJson());
            o.put("firma", c.getSignature());
            AccountIdentifiers ids = c.getAccountIdentifiers();
            o.put("perfil", ids != null && ids.getObfuscatedProfileId() != null ? ids.getObfuscatedProfileId() : "");
            evento(o);
        } catch (Throwable e) { }
    }

    // compras sin consumir (se cerró la app a mitad de la compra, o un pago pendiente que ya se confirmó)
    private void pendientes() {
        bc.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                new PurchasesResponseListener() {
                    public void onQueryPurchasesResponse(BillingResult r, List<Purchase> lista) {
                        if (r.getResponseCode() == BillingClient.BillingResponseCode.OK && lista != null)
                            for (Purchase c : lista) avisarCompra(c);
                    }
                });
    }

    public void onPurchasesUpdated(BillingResult r, List<Purchase> lista) {
        int c = r.getResponseCode();
        if (c == BillingClient.BillingResponseCode.OK && lista != null) { for (Purchase p : lista) avisarCompra(p); }
        else if (c == BillingClient.BillingResponseCode.USER_CANCELED) error("cancelada", null);
        else if (c == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) pendientes();
        else error("compra", "Google Play no pudo completar la compra. Intenta de nuevo.");
    }

    @JavascriptInterface
    public boolean disponible() { return true; }

    @JavascriptInterface
    public void iniciar(final String producto) {
        act.runOnUiThread(new Runnable() {
            public void run() {
                conectar(new Runnable() {
                    public void run() { consultar(producto, null); pendientes(); }
                });
            }
        });
    }

    @JavascriptInterface
    public void comprar(final String producto, final String cuenta, final String licencia) {
        act.runOnUiThread(new Runnable() {
            public void run() {
                conectar(new Runnable() {
                    public void run() {
                        final Runnable lanzar = new Runnable() {
                            public void run() {
                                ProductDetails d = detalles.get(producto);
                                if (d == null) { error("producto", "La licencia todavía no está a la venta en Google Play."); return; }
                                BillingFlowParams f = BillingFlowParams.newBuilder()
                                        .setProductDetailsParamsList(Collections.singletonList(
                                                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
                                        .setObfuscatedAccountId(cuenta).setObfuscatedProfileId(licencia).build();
                                BillingResult r = bc.launchBillingFlow(act, f);
                                if (r.getResponseCode() != BillingClient.BillingResponseCode.OK
                                        && r.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED)
                                    error("compra", "No se pudo abrir Google Play.");
                            }
                        };
                        if (detalles.containsKey(producto)) lanzar.run(); else consultar(producto, lanzar);
                    }
                });
            }
        });
    }

    @JavascriptInterface
    public void consumir(final String token) {
        act.runOnUiThread(new Runnable() {
            public void run() {
                conectar(new Runnable() {
                    public void run() {
                        bc.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(token).build(), new ConsumeResponseListener() {
                            public void onConsumeResponse(BillingResult r, String t) {
                                try {
                                    JSONObject o = new JSONObject();
                                    o.put("tipo", "consumida");
                                    o.put("token", t);
                                    o.put("ok", r.getResponseCode() == BillingClient.BillingResponseCode.OK);
                                    evento(o);
                                } catch (Throwable e) { }
                            }
                        });
                    }
                });
            }
        });
    }

    // la firma RSA de Google Play (SHA1withRSA) con la llave pública de la app; igual que Enlace, pero sin
    // depender de ella: Pagos se compila aparte, con Gradle
    @JavascriptInterface
    public boolean verificar(String llave, String json, String firma) {
        try {
            byte[] k = android.util.Base64.decode(llave, android.util.Base64.DEFAULT);
            java.security.PublicKey pub = java.security.KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.X509EncodedKeySpec(k));
            java.security.Signature s = java.security.Signature.getInstance("SHA1withRSA");
            s.initVerify(pub);
            s.update(json.getBytes("UTF-8"));
            return s.verify(android.util.Base64.decode(firma, android.util.Base64.DEFAULT));
        } catch (Throwable e) {
            return false;
        }
    }
}
