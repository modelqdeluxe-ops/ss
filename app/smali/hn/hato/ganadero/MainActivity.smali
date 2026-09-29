.class public Lhn/hato/ganadero/MainActivity;
.super Landroid/app/Activity;
.source "MainActivity.java"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lhn/hato/ganadero/MainActivity$Puente;
    }
.end annotation


# static fields
.field private static final INICIO:Ljava/lang/String; = "file:///android_asset/index.html"

.field private static final REQ_ABRIR:I = 0xb

.field private static final REQ_GUARDAR:I = 0xc

.field private static final REQ_VOZ:I = 0xd


# instance fields
.field private archivoCb:Landroid/webkit/ValueCallback;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Landroid/webkit/ValueCallback<",
            "[",
            "Landroid/net/Uri;",
            ">;"
        }
    .end annotation
.end field

.field private pendienteDatos:Ljava/lang/String;

.field private permisoPend:Landroid/webkit/PermissionRequest;

.field private geoCb:Landroid/webkit/GeolocationPermissions$Callback;

.field private geoOrigen:Ljava/lang/String;

.field private ultimoAtras:J

.field private web:Landroid/webkit/WebView;


# direct methods
.method public static synthetic $r8$lambda$L2LBIRWSiRe1IKF1SQhdvZwKs_s(Lhn/hato/ganadero/MainActivity;Ljava/lang/String;)V
    .locals 0

    invoke-direct {p0, p1}, Lhn/hato/ganadero/MainActivity;->lambda$onBackPressed$0(Ljava/lang/String;)V

    return-void
.end method

.method static bridge synthetic -$$Nest$fgetarchivoCb(Lhn/hato/ganadero/MainActivity;)Landroid/webkit/ValueCallback;
    .locals 0

    iget-object p0, p0, Lhn/hato/ganadero/MainActivity;->archivoCb:Landroid/webkit/ValueCallback;

    return-object p0
.end method

.method static bridge synthetic -$$Nest$fgetweb(Lhn/hato/ganadero/MainActivity;)Landroid/webkit/WebView;
    .locals 0

    iget-object p0, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    return-object p0
.end method

.method static bridge synthetic -$$Nest$fputarchivoCb(Lhn/hato/ganadero/MainActivity;Landroid/webkit/ValueCallback;)V
    .locals 0

    iput-object p1, p0, Lhn/hato/ganadero/MainActivity;->archivoCb:Landroid/webkit/ValueCallback;

    return-void
.end method

.method static bridge synthetic -$$Nest$fputpendienteDatos(Lhn/hato/ganadero/MainActivity;Ljava/lang/String;)V
    .locals 0

    iput-object p1, p0, Lhn/hato/ganadero/MainActivity;->pendienteDatos:Ljava/lang/String;

    return-void
.end method

.method static bridge synthetic -$$Nest$smparse(Ljava/lang/String;)I
    .locals 0

    invoke-static {p0}, Lhn/hato/ganadero/MainActivity;->parse(Ljava/lang/String;)I

    move-result p0

    return p0
.end method

.method public constructor <init>()V
    .locals 2

    .line 29
    invoke-direct {p0}, Landroid/app/Activity;-><init>()V

    const-wide/16 v0, 0x0

    .line 38
    iput-wide v0, p0, Lhn/hato/ganadero/MainActivity;->ultimoAtras:J

    return-void
.end method

.method private esNoche()Z
    .locals 2

    .line 114
    invoke-virtual {p0}, Lhn/hato/ganadero/MainActivity;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    invoke-virtual {v0}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;

    move-result-object v0

    iget v0, v0, Landroid/content/res/Configuration;->uiMode:I

    and-int/lit8 v0, v0, 0x30

    const/16 v1, 0x20

    if-ne v0, v1, :cond_0

    const/4 v0, 0x1

    goto :goto_0

    :cond_0
    const/4 v0, 0x0

    :goto_0
    return v0
.end method

.method private synthetic lambda$onBackPressed$0(Ljava/lang/String;)V
    .locals 6

    if-eqz p1, :cond_0

    .line 126
    const-string v0, "1"

    invoke-virtual {p1, v0}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result p1

    if-eqz p1, :cond_0

    return-void

    .line 127
    :cond_0
    iget-object p1, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    invoke-virtual {p1}, Landroid/webkit/WebView;->canGoBack()Z

    move-result p1

    if-eqz p1, :cond_1

    iget-object p1, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    invoke-virtual {p1}, Landroid/webkit/WebView;->goBack()V

    return-void

    .line 128
    :cond_1
    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v0

    .line 129
    iget-wide v2, p0, Lhn/hato/ganadero/MainActivity;->ultimoAtras:J

    sub-long v2, v0, v2

    const-wide/16 v4, 0x7d0

    cmp-long p1, v2, v4

    if-gez p1, :cond_2

    invoke-virtual {p0}, Lhn/hato/ganadero/MainActivity;->finish()V

    return-void

    .line 130
    :cond_2
    iput-wide v0, p0, Lhn/hato/ganadero/MainActivity;->ultimoAtras:J

    .line 131
    const-string p1, "Toca atr\u00e1s otra vez para salir"

    const/4 v0, 0x0

    invoke-static {p0, p1, v0}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;

    move-result-object p1

    invoke-virtual {p1}, Landroid/widget/Toast;->show()V

    return-void
.end method

.method private static parse(Ljava/lang/String;)I
    .locals 4

    if-nez p0, :cond_0

    .line 228
    const-string p0, ""

    goto :goto_0

    :cond_0
    invoke-virtual {p0}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object p0

    .line 229
    :goto_0
    const-string v0, "rgb"

    invoke-virtual {p0, v0}, Ljava/lang/String;->startsWith(Ljava/lang/String;)Z

    move-result v0

    if-eqz v0, :cond_1

    const/16 v0, 0x28

    .line 230
    invoke-virtual {p0, v0}, Ljava/lang/String;->indexOf(I)I

    move-result v0

    const/4 v1, 0x1

    add-int/2addr v0, v1

    const/16 v2, 0x29

    invoke-virtual {p0, v2}, Ljava/lang/String;->indexOf(I)I

    move-result v2

    invoke-virtual {p0, v0, v2}, Ljava/lang/String;->substring(II)Ljava/lang/String;

    move-result-object p0

    const-string v0, ","

    invoke-virtual {p0, v0}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;

    move-result-object p0

    const/4 v0, 0x0

    .line 231
    aget-object v2, p0, v0

    invoke-virtual {v2}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object v2

    invoke-static {v2}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I

    move-result v2

    aget-object v1, p0, v1

    invoke-virtual {v1}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object v1

    invoke-static {v1}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I

    move-result v1

    const/4 v3, 0x2

    aget-object p0, p0, v3

    invoke-virtual {p0}, Ljava/lang/String;->trim()Ljava/lang/String;

    move-result-object p0

    const-string v3, " "

    invoke-virtual {p0, v3}, Ljava/lang/String;->split(Ljava/lang/String;)[Ljava/lang/String;

    move-result-object p0

    aget-object p0, p0, v0

    invoke-static {p0}, Ljava/lang/Integer;->parseInt(Ljava/lang/String;)I

    move-result p0

    invoke-static {v2, v1, p0}, Landroid/graphics/Color;->rgb(III)I

    move-result p0

    return p0

    .line 233
    :cond_1
    invoke-static {p0}, Landroid/graphics/Color;->parseColor(Ljava/lang/String;)I

    move-result p0

    return p0
.end method


# virtual methods
.method protected onActivityResult(IILandroid/content/Intent;)V
    .locals 6

    .line 143
    invoke-super {p0, p1, p2, p3}, Landroid/app/Activity;->onActivityResult(IILandroid/content/Intent;)V

    const/16 v0, 0xb

    const/4 v1, 0x1

    const/4 v2, -0x1

    const/4 v3, 0x0

    const/4 v4, 0x0

    if-ne p1, v0, :cond_2

    .line 145
    iget-object p1, p0, Lhn/hato/ganadero/MainActivity;->archivoCb:Landroid/webkit/ValueCallback;

    if-nez p1, :cond_0

    return-void

    :cond_0
    if-ne p2, v2, :cond_1

    if-eqz p3, :cond_1

    .line 147
    invoke-virtual {p3}, Landroid/content/Intent;->getData()Landroid/net/Uri;

    move-result-object p1

    if-eqz p1, :cond_1

    new-array p1, v1, [Landroid/net/Uri;

    invoke-virtual {p3}, Landroid/content/Intent;->getData()Landroid/net/Uri;

    move-result-object p2

    aput-object p2, p1, v3

    goto :goto_0

    :cond_1
    move-object p1, v4

    .line 148
    :goto_0
    iget-object p2, p0, Lhn/hato/ganadero/MainActivity;->archivoCb:Landroid/webkit/ValueCallback;

    invoke-interface {p2, p1}, Landroid/webkit/ValueCallback;->onReceiveValue(Ljava/lang/Object;)V

    .line 149
    iput-object v4, p0, Lhn/hato/ganadero/MainActivity;->archivoCb:Landroid/webkit/ValueCallback;

    goto/16 :goto_5

    :cond_2
    const/16 v0, 0xd

    .line 150
    const-string v5, ")"

    if-ne p1, v0, :cond_5

    if-ne p2, v2, :cond_3

    if-eqz p3, :cond_3

    .line 153
    const-string p1, "android.speech.extra.RESULTS"

    invoke-virtual {p3, p1}, Landroid/content/Intent;->getStringArrayListExtra(Ljava/lang/String;)Ljava/util/ArrayList;

    move-result-object p1

    if-eqz p1, :cond_3

    .line 154
    invoke-virtual {p1}, Ljava/util/ArrayList;->isEmpty()Z

    move-result p2

    if-nez p2, :cond_3

    invoke-virtual {p1, v3}, Ljava/util/ArrayList;->get(I)Ljava/lang/Object;

    move-result-object p1

    check-cast p1, Ljava/lang/String;

    goto :goto_1

    :cond_3
    move-object p1, v4

    .line 156
    :goto_1
    iget-object p2, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    if-nez p1, :cond_4

    const-string p1, "null"

    goto :goto_2

    :cond_4
    invoke-static {p1}, Lorg/json/JSONObject;->quote(Ljava/lang/String;)Ljava/lang/String;

    move-result-object p1

    :goto_2
    new-instance p3, Ljava/lang/StringBuilder;

    const-string v0, "window.rumiVoz&&window.rumiVoz("

    invoke-direct {p3, v0}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {p3, p1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p3, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p1

    invoke-virtual {p2, p1, v4}, Landroid/webkit/WebView;->evaluateJavascript(Ljava/lang/String;Landroid/webkit/ValueCallback;)V

    goto :goto_5

    :cond_5
    const/16 v0, 0xc

    if-ne p1, v0, :cond_a

    .line 158
    iget-object p1, p0, Lhn/hato/ganadero/MainActivity;->pendienteDatos:Ljava/lang/String;

    .line 159
    iput-object v4, p0, Lhn/hato/ganadero/MainActivity;->pendienteDatos:Ljava/lang/String;

    if-ne p2, v2, :cond_a

    if-eqz p3, :cond_a

    .line 160
    invoke-virtual {p3}, Landroid/content/Intent;->getData()Landroid/net/Uri;

    move-result-object p2

    if-eqz p2, :cond_a

    if-nez p1, :cond_6

    goto :goto_5

    .line 162
    :cond_6
    :try_start_0
    invoke-virtual {p0}, Lhn/hato/ganadero/MainActivity;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object p2

    invoke-virtual {p3}, Landroid/content/Intent;->getData()Landroid/net/Uri;

    move-result-object p3

    const-string v0, "wt"

    invoke-virtual {p2, p3, v0}, Landroid/content/ContentResolver;->openOutputStream(Landroid/net/Uri;Ljava/lang/String;)Ljava/io/OutputStream;

    move-result-object p2
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    if-eqz p2, :cond_8

    .line 163
    :try_start_1
    sget-object p3, Ljava/nio/charset/StandardCharsets;->UTF_8:Ljava/nio/charset/Charset;

    invoke-virtual {p1, p3}, Ljava/lang/String;->getBytes(Ljava/nio/charset/Charset;)[B

    move-result-object p1

    invoke-virtual {p2, p1}, Ljava/io/OutputStream;->write([B)V
    :try_end_1
    .catchall {:try_start_1 .. :try_end_1} :catchall_0

    goto :goto_4

    :catchall_0
    move-exception p1

    if-eqz p2, :cond_7

    .line 162
    :try_start_2
    invoke-virtual {p2}, Ljava/io/OutputStream;->close()V
    :try_end_2
    .catchall {:try_start_2 .. :try_end_2} :catchall_1

    goto :goto_3

    :catchall_1
    move-exception p2

    :try_start_3
    invoke-virtual {p1, p2}, Ljava/lang/Throwable;->addSuppressed(Ljava/lang/Throwable;)V

    :cond_7
    :goto_3
    throw p1

    :cond_8
    const/4 v1, 0x0

    :goto_4
    if-eqz p2, :cond_9

    .line 164
    invoke-virtual {p2}, Ljava/io/OutputStream;->close()V
    :try_end_3
    .catch Ljava/lang/Exception; {:try_start_3 .. :try_end_3} :catch_0

    :cond_9
    move v3, v1

    .line 165
    :catch_0
    iget-object p1, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    new-instance p2, Ljava/lang/StringBuilder;

    const-string p3, "window.hatoGuardado&&window.hatoGuardado("

    invoke-direct {p2, p3}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    invoke-virtual {p2, v3}, Ljava/lang/StringBuilder;->append(Z)Ljava/lang/StringBuilder;

    invoke-virtual {p2, v5}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {p2}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p2

    invoke-virtual {p1, p2, v4}, Landroid/webkit/WebView;->evaluateJavascript(Ljava/lang/String;Landroid/webkit/ValueCallback;)V

    nop

    :cond_a
    :goto_5
    return-void
.end method

.method public onBackPressed()V
    .locals 3

    .line 125
    iget-object v0, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    new-instance v1, Lhn/hato/ganadero/MainActivity$$ExternalSyntheticLambda0;

    invoke-direct {v1, p0}, Lhn/hato/ganadero/MainActivity$$ExternalSyntheticLambda0;-><init>(Lhn/hato/ganadero/MainActivity;)V

    const-string v2, "(window.androidBack&&window.androidBack())?\'1\':\'0\'"

    invoke-virtual {v0, v2, v1}, Landroid/webkit/WebView;->evaluateJavascript(Ljava/lang/String;Landroid/webkit/ValueCallback;)V

    return-void
.end method

.method protected onCreate(Landroid/os/Bundle;)V
    .locals 3

    .line 42
    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V

    invoke-static {p0}, Lhn/hato/ganadero/Pantalla;->maxima(Landroid/app/Activity;)V

    .line 43
    new-instance v0, Landroid/webkit/WebView;

    invoke-direct {v0, p0}, Landroid/webkit/WebView;-><init>(Landroid/content/Context;)V

    iput-object v0, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    .line 44
    invoke-direct {p0}, Lhn/hato/ganadero/MainActivity;->esNoche()Z

    move-result v1

    if-eqz v1, :cond_0

    const-string v1, "#0E1822"

    goto :goto_0

    :cond_0
    const-string v1, "#0E1822"

    :goto_0
    invoke-static {v1}, Landroid/graphics/Color;->parseColor(Ljava/lang/String;)I

    move-result v1

    invoke-virtual {v0, v1}, Landroid/webkit/WebView;->setBackgroundColor(I)V

    .line 45
    iget-object v0, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    invoke-virtual {p0, v0}, Lhn/hato/ganadero/MainActivity;->setContentView(Landroid/view/View;)V

    .line 47
    iget-object v0, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    invoke-virtual {v0}, Landroid/webkit/WebView;->getSettings()Landroid/webkit/WebSettings;

    move-result-object v0

    const/4 v1, 0x1

    .line 48
    invoke-virtual {v0, v1}, Landroid/webkit/WebSettings;->setJavaScriptEnabled(Z)V

    .line 49
    invoke-virtual {v0, v1}, Landroid/webkit/WebSettings;->setDomStorageEnabled(Z)V

    .line 50
    invoke-virtual {v0, v1}, Landroid/webkit/WebSettings;->setDatabaseEnabled(Z)V

    const/4 v2, 0x0

    invoke-virtual {v0, v2}, Landroid/webkit/WebSettings;->setMediaPlaybackRequiresUserGesture(Z)V

    .line 51
    invoke-virtual {v0, v1}, Landroid/webkit/WebSettings;->setAllowFileAccess(Z)V

    .line 52
    invoke-virtual {v0, v1}, Landroid/webkit/WebSettings;->setAllowContentAccess(Z)V

    const/4 v2, 0x0

    .line 53
    invoke-virtual {v0, v2}, Landroid/webkit/WebSettings;->setBuiltInZoomControls(Z)V

    .line 54
    invoke-virtual {v0, v2}, Landroid/webkit/WebSettings;->setSupportZoom(Z)V

    .line 55
    invoke-virtual {v0, v2}, Landroid/webkit/WebSettings;->setMediaPlaybackRequiresUserGesture(Z)V

    invoke-virtual {v0, v1}, Landroid/webkit/WebSettings;->setGeolocationEnabled(Z)V

    .line 57
    invoke-virtual {p0}, Lhn/hato/ganadero/MainActivity;->getResources()Landroid/content/res/Resources;

    move-result-object v1

    invoke-virtual {v1}, Landroid/content/res/Resources;->getConfiguration()Landroid/content/res/Configuration;

    move-result-object v1

    iget v1, v1, Landroid/content/res/Configuration;->fontScale:F

    const/high16 v2, 0x42c80000    # 100.0f

    mul-float v1, v1, v2

    .line 58
    invoke-static {v1}, Ljava/lang/Math;->round(F)I

    move-result v1

    const/16 v2, 0x7d

    invoke-static {v2, v1}, Ljava/lang/Math;->min(II)I

    move-result v1

    const/16 v2, 0x5a

    invoke-static {v2, v1}, Ljava/lang/Math;->max(II)I

    move-result v1

    invoke-virtual {v0, v1}, Landroid/webkit/WebSettings;->setTextZoom(I)V

    .line 60
    iget-object v0, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    new-instance v1, Lhn/hato/ganadero/MainActivity$Puente;

    const/4 v2, 0x0

    invoke-direct {v1, p0, v2}, Lhn/hato/ganadero/MainActivity$Puente;-><init>(Lhn/hato/ganadero/MainActivity;Lhn/hato/ganadero/MainActivity$Puente-IA;)V

    const-string v2, "Android"

    invoke-virtual {v0, v1, v2}, Landroid/webkit/WebView;->addJavascriptInterface(Ljava/lang/Object;Ljava/lang/String;)V

    invoke-static {p0, v0}, Lhn/hato/ganadero/Enlace;->registrar(Landroid/app/Activity;Landroid/webkit/WebView;)V

    .line 61
    iget-object v0, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    new-instance v1, Lhn/hato/ganadero/MainActivity$1;

    invoke-direct {v1, p0}, Lhn/hato/ganadero/MainActivity$1;-><init>(Lhn/hato/ganadero/MainActivity;)V

    invoke-virtual {v0, v1}, Landroid/webkit/WebView;->setWebViewClient(Landroid/webkit/WebViewClient;)V

    .line 88
    iget-object v0, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    new-instance v1, Lhn/hato/ganadero/MainActivity$2;

    invoke-direct {v1, p0}, Lhn/hato/ganadero/MainActivity$2;-><init>(Lhn/hato/ganadero/MainActivity;)V

    invoke-virtual {v0, v1}, Landroid/webkit/WebView;->setWebChromeClient(Landroid/webkit/WebChromeClient;)V

    if-eqz p1, :cond_1

    .line 106
    iget-object v0, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    invoke-virtual {v0, p1}, Landroid/webkit/WebView;->restoreState(Landroid/os/Bundle;)Landroid/webkit/WebBackForwardList;

    move-result-object p1

    if-eqz p1, :cond_1

    goto :goto_1

    .line 109
    :cond_1
    iget-object p1, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    const-string v0, "file:///android_asset/index.html"

    invoke-virtual {p1, v0}, Landroid/webkit/WebView;->loadUrl(Ljava/lang/String;)V

    :goto_1
    iget-object v0, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    invoke-static {p0, v0}, Lhn/hato/ganadero/Avisos;->deIntent(Landroid/app/Activity;Landroid/webkit/WebView;)V

    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->programar(Landroid/content/Context;)V

    return-void
.end method

# Al tocar un aviso de Rumi o el widget con la app abierta
.method protected onNewIntent(Landroid/content/Intent;)V
    .locals 1

    invoke-super {p0, p1}, Landroid/app/Activity;->onNewIntent(Landroid/content/Intent;)V

    invoke-virtual {p0, p1}, Lhn/hato/ganadero/MainActivity;->setIntent(Landroid/content/Intent;)V

    iget-object v0, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    invoke-static {p0, v0}, Lhn/hato/ganadero/Avisos;->deIntent(Landroid/app/Activity;Landroid/webkit/WebView;)V

    return-void
.end method

.method protected onPause()V
    .locals 1

    .line 136
    invoke-super {p0}, Landroid/app/Activity;->onPause()V

    iget-object v0, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    invoke-virtual {v0}, Landroid/webkit/WebView;->onPause()V

    return-void
.end method

.method protected onResume()V
    .locals 1

    .line 139
    invoke-super {p0}, Landroid/app/Activity;->onResume()V

    invoke-static {p0}, Lhn/hato/ganadero/Pantalla;->maxima(Landroid/app/Activity;)V

    iget-object v0, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    invoke-virtual {v0}, Landroid/webkit/WebView;->onResume()V

    return-void
.end method

.method protected onSaveInstanceState(Landroid/os/Bundle;)V
    .locals 1

    .line 119
    invoke-super {p0, p1}, Landroid/app/Activity;->onSaveInstanceState(Landroid/os/Bundle;)V

    .line 120
    iget-object v0, p0, Lhn/hato/ganadero/MainActivity;->web:Landroid/webkit/WebView;

    invoke-virtual {v0, p1}, Landroid/webkit/WebView;->saveState(Landroid/os/Bundle;)Landroid/webkit/WebBackForwardList;

    return-void
.end method


# Cámara para las fotos de los animales (getUserMedia en la página)
.method public pedirCamara(Landroid/webkit/PermissionRequest;)V
    .locals 3

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x17

    if-lt v0, v1, :cond_dar

    const-string v0, "android.permission.CAMERA"

    invoke-virtual {p0, v0}, Landroid/app/Activity;->checkSelfPermission(Ljava/lang/String;)I

    move-result v1

    if-eqz v1, :cond_dar

    iget-object v1, p0, Lhn/hato/ganadero/MainActivity;->permisoPend:Landroid/webkit/PermissionRequest;

    if-eqz v1, :cond_pedir

    invoke-virtual {v1}, Landroid/webkit/PermissionRequest;->deny()V

    :cond_pedir
    iput-object p1, p0, Lhn/hato/ganadero/MainActivity;->permisoPend:Landroid/webkit/PermissionRequest;

    const/4 v1, 0x1

    new-array v1, v1, [Ljava/lang/String;

    const/4 v2, 0x0

    aput-object v0, v1, v2

    const/16 v2, 0xe

    invoke-virtual {p0, v1, v2}, Landroid/app/Activity;->requestPermissions([Ljava/lang/String;I)V

    return-void

    :cond_dar
    invoke-virtual {p1}, Landroid/webkit/PermissionRequest;->getResources()[Ljava/lang/String;

    move-result-object v0

    invoke-virtual {p1, v0}, Landroid/webkit/PermissionRequest;->grant([Ljava/lang/String;)V

    return-void
.end method

.method public onRequestPermissionsResult(I[Ljava/lang/String;[I)V
    .locals 3

    invoke-super {p0, p1, p2, p3}, Landroid/app/Activity;->onRequestPermissionsResult(I[Ljava/lang/String;[I)V

    const/16 v0, 0xf

    if-ne p1, v0, :cond_cam

    iget-object v0, p0, Lhn/hato/ganadero/MainActivity;->geoCb:Landroid/webkit/GeolocationPermissions$Callback;

    if-eqz v0, :cond_fin

    iget-object v1, p0, Lhn/hato/ganadero/MainActivity;->geoOrigen:Ljava/lang/String;

    const/4 v2, 0x0

    iput-object v2, p0, Lhn/hato/ganadero/MainActivity;->geoCb:Landroid/webkit/GeolocationPermissions$Callback;

    if-eqz p3, :cond_geo_no

    array-length v2, p3

    if-lez v2, :cond_geo_no

    const/4 v2, 0x0

    aget v2, p3, v2

    if-nez v2, :cond_geo_no

    const/4 v2, 0x1

    goto :goto_geo

    :cond_geo_no
    const/4 v2, 0x0

    :goto_geo
    const/4 p1, 0x0

    invoke-interface {v0, v1, v2, p1}, Landroid/webkit/GeolocationPermissions$Callback;->invoke(Ljava/lang/String;ZZ)V

    goto :cond_fin

    :cond_cam
    const/16 v0, 0xe

    if-ne p1, v0, :cond_fin

    iget-object v0, p0, Lhn/hato/ganadero/MainActivity;->permisoPend:Landroid/webkit/PermissionRequest;

    if-eqz v0, :cond_fin

    const/4 v1, 0x0

    iput-object v1, p0, Lhn/hato/ganadero/MainActivity;->permisoPend:Landroid/webkit/PermissionRequest;

    if-eqz p3, :cond_negar

    array-length v1, p3

    if-lez v1, :cond_negar

    const/4 v1, 0x0

    aget v1, p3, v1

    if-nez v1, :cond_negar

    invoke-virtual {v0}, Landroid/webkit/PermissionRequest;->getResources()[Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Landroid/webkit/PermissionRequest;->grant([Ljava/lang/String;)V

    goto :cond_fin

    :cond_negar
    invoke-virtual {v0}, Landroid/webkit/PermissionRequest;->deny()V

    :cond_fin
    return-void
.end method


# Ubicación aproximada para el clima de la zona (navigator.geolocation en la página)
.method public pedirUbicacion(Ljava/lang/String;Landroid/webkit/GeolocationPermissions$Callback;)V
    .locals 4

    const/4 v3, 0x0

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x17

    if-lt v0, v1, :cond_dar

    const-string v0, "android.permission.ACCESS_COARSE_LOCATION"

    invoke-virtual {p0, v0}, Landroid/app/Activity;->checkSelfPermission(Ljava/lang/String;)I

    move-result v1

    if-eqz v1, :cond_dar

    iget-object v1, p0, Lhn/hato/ganadero/MainActivity;->geoCb:Landroid/webkit/GeolocationPermissions$Callback;

    if-eqz v1, :cond_pedir

    iget-object v2, p0, Lhn/hato/ganadero/MainActivity;->geoOrigen:Ljava/lang/String;

    invoke-interface {v1, v2, v3, v3}, Landroid/webkit/GeolocationPermissions$Callback;->invoke(Ljava/lang/String;ZZ)V

    :cond_pedir
    iput-object p1, p0, Lhn/hato/ganadero/MainActivity;->geoOrigen:Ljava/lang/String;

    iput-object p2, p0, Lhn/hato/ganadero/MainActivity;->geoCb:Landroid/webkit/GeolocationPermissions$Callback;

    const/4 v1, 0x1

    new-array v1, v1, [Ljava/lang/String;

    aput-object v0, v1, v3

    const/16 v2, 0xf

    invoke-virtual {p0, v1, v2}, Landroid/app/Activity;->requestPermissions([Ljava/lang/String;I)V

    return-void

    :cond_dar
    const/4 v0, 0x1

    invoke-interface {p2, p1, v0, v3}, Landroid/webkit/GeolocationPermissions$Callback;->invoke(Ljava/lang/String;ZZ)V

    return-void
.end method
