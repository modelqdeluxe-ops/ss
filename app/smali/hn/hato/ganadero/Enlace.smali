.class public Lhn/hato/ganadero/Enlace;
.super Ljava/lang/Object;
.source "Enlace.java"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lhn/hato/ganadero/Enlace$Cripto;,
        Lhn/hato/ganadero/Enlace$Recibido;
    }
.end annotation


# static fields
.field private static final MAXIMO:I = 0x1000000

.field private static volatile recibido:Ljava/lang/String;


# direct methods
.method static constructor <clinit>()V
    .registers 1

    .prologue
    .line 44
    const/4 v0, 0x0

    sput-object v0, Lhn/hato/ganadero/Enlace;->recibido:Ljava/lang/String;

    return-void
.end method

.method public constructor <init>()V
    .registers 1

    .prologue
    .line 25
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method static synthetic access$000()Ljava/lang/String;
    .registers 1

    .prologue
    .line 25
    sget-object v0, Lhn/hato/ganadero/Enlace;->recibido:Ljava/lang/String;

    return-object v0
.end method

.method static synthetic access$002(Ljava/lang/String;)Ljava/lang/String;
    .registers 1

    .prologue
    .line 25
    sput-object p0, Lhn/hato/ganadero/Enlace;->recibido:Ljava/lang/String;

    return-object p0
.end method

.method public static deIntent(Landroid/app/Activity;Landroid/webkit/WebView;)V
    .registers 7

    .prologue
    .line 50
    :try_start_0
    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

    move-result-object v2

    .line 51
    if-nez v2, :cond_7

    .line 80
    :cond_6
    :goto_6
    return-void

    .line 52
    :cond_7
    invoke-virtual {v2}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object v0

    .line 53
    const/4 v1, 0x0

    .line 54
    const-string v3, "android.intent.action.VIEW"

    invoke-virtual {v3, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v3

    if-eqz v3, :cond_51

    invoke-virtual {v2}, Landroid/content/Intent;->getData()Landroid/net/Uri;

    move-result-object v0

    .line 60
    :goto_18
    if-eqz v0, :cond_6

    .line 62
    new-instance v1, Landroid/content/Intent;

    invoke-virtual {p0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    move-result-object v2

    invoke-direct {v1, p0, v2}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {p0, v1}, Landroid/app/Activity;->setIntent(Landroid/content/Intent;)V

    .line 63
    invoke-virtual {p0}, Landroid/app/Activity;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v1

    invoke-virtual {v1, v0}, Landroid/content/ContentResolver;->openInputStream(Landroid/net/Uri;)Ljava/io/InputStream;

    move-result-object v1

    .line 64
    if-eqz v1, :cond_6

    .line 65
    new-instance v0, Ljava/io/ByteArrayOutputStream;

    invoke-direct {v0}, Ljava/io/ByteArrayOutputStream;-><init>()V
    :try_end_35
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_35} :catch_4f

    .line 67
    const/16 v2, 0x4000

    :try_start_37
    new-array v2, v2, [B

    .line 69
    :cond_39
    invoke-virtual {v1, v2}, Ljava/io/InputStream;->read([B)I

    move-result v3

    if-lez v3, :cond_84

    .line 70
    const/4 v4, 0x0

    invoke-virtual {v0, v2, v4, v3}, Ljava/io/ByteArrayOutputStream;->write([BII)V

    .line 71
    invoke-virtual {v0}, Ljava/io/ByteArrayOutputStream;->size()I
    :try_end_46
    .catchall {:try_start_37 .. :try_end_46} :catchall_9e

    move-result v3

    const/high16 v4, 0x1000000

    if-le v3, v4, :cond_39

    .line 74
    :try_start_4b
    invoke-virtual {v1}, Ljava/io/InputStream;->close()V

    goto :goto_6

    .line 78
    :catch_4f
    move-exception v0

    goto :goto_6

    .line 55
    :cond_51
    const-string v3, "android.intent.action.SEND"

    invoke-virtual {v3, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_a3

    .line 56
    const-string v0, "android.intent.extra.STREAM"

    invoke-virtual {v2, v0}, Landroid/content/Intent;->getParcelableExtra(Ljava/lang/String;)Landroid/os/Parcelable;

    move-result-object v0

    .line 57
    instance-of v3, v0, Landroid/net/Uri;

    if-eqz v3, :cond_66

    check-cast v0, Landroid/net/Uri;

    goto :goto_18

    .line 58
    :cond_66
    invoke-virtual {v2}, Landroid/content/Intent;->getClipData()Landroid/content/ClipData;

    move-result-object v0

    if-eqz v0, :cond_a3

    invoke-virtual {v2}, Landroid/content/Intent;->getClipData()Landroid/content/ClipData;

    move-result-object v0

    invoke-virtual {v0}, Landroid/content/ClipData;->getItemCount()I

    move-result v0

    if-lez v0, :cond_a3

    invoke-virtual {v2}, Landroid/content/Intent;->getClipData()Landroid/content/ClipData;

    move-result-object v0

    const/4 v1, 0x0

    invoke-virtual {v0, v1}, Landroid/content/ClipData;->getItemAt(I)Landroid/content/ClipData$Item;

    move-result-object v0

    invoke-virtual {v0}, Landroid/content/ClipData$Item;->getUri()Landroid/net/Uri;

    move-result-object v0

    goto :goto_18

    .line 74
    :cond_84
    invoke-virtual {v1}, Ljava/io/InputStream;->close()V

    .line 76
    invoke-virtual {v0}, Ljava/io/ByteArrayOutputStream;->toByteArray()[B

    move-result-object v0

    const/4 v1, 0x2

    invoke-static {v0, v1}, Landroid/util/Base64;->encodeToString([BI)Ljava/lang/String;

    move-result-object v0

    sput-object v0, Lhn/hato/ganadero/Enlace;->recibido:Ljava/lang/String;

    .line 77
    if-eqz p1, :cond_6

    new-instance v0, Lhn/hato/ganadero/Enlace$1;

    invoke-direct {v0, p1}, Lhn/hato/ganadero/Enlace$1;-><init>(Landroid/webkit/WebView;)V

    invoke-virtual {p1, v0}, Landroid/webkit/WebView;->post(Ljava/lang/Runnable;)Z

    goto/16 :goto_6

    .line 74
    :catchall_9e
    move-exception v0

    invoke-virtual {v1}, Ljava/io/InputStream;->close()V

    .line 75
    throw v0
    :try_end_a3
    .catch Ljava/lang/Throwable; {:try_start_4b .. :try_end_a3} :catch_4f

    :cond_a3
    move-object v0, v1

    goto/16 :goto_18
.end method

.method public static registrar(Landroid/app/Activity;Landroid/webkit/WebView;)V
    .registers 6

    .prologue
    .line 28
    :try_start_0
    const-string v0, "hn.hato.ganadero.Pagos"

    invoke-static {v0}, Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;

    move-result-object v0

    .line 29
    const/4 v1, 0x2

    new-array v1, v1, [Ljava/lang/Class;

    const/4 v2, 0x0

    const-class v3, Landroid/app/Activity;

    aput-object v3, v1, v2

    const/4 v2, 0x1

    const-class v3, Landroid/webkit/WebView;

    aput-object v3, v1, v2

    invoke-virtual {v0, v1}, Ljava/lang/Class;->getConstructor([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;

    move-result-object v0

    const/4 v1, 0x2

    new-array v1, v1, [Ljava/lang/Object;

    const/4 v2, 0x0

    aput-object p0, v1, v2

    const/4 v2, 0x1

    aput-object p1, v1, v2

    invoke-virtual {v0, v1}, Ljava/lang/reflect/Constructor;->newInstance([Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v0

    .line 30
    const-string v1, "Pagos"

    invoke-virtual {p1, v0, v1}, Landroid/webkit/WebView;->addJavascriptInterface(Ljava/lang/Object;Ljava/lang/String;)V
    :try_end_29
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_29} :catch_42

    .line 35
    :goto_29
    :try_start_29
    new-instance v0, Lhn/hato/ganadero/Enlace$Cripto;

    invoke-direct {v0}, Lhn/hato/ganadero/Enlace$Cripto;-><init>()V

    const-string v1, "Cripto"

    invoke-virtual {p1, v0, v1}, Landroid/webkit/WebView;->addJavascriptInterface(Ljava/lang/Object;Ljava/lang/String;)V
    :try_end_33
    .catch Ljava/lang/Throwable; {:try_start_29 .. :try_end_33} :catch_40

    .line 39
    :goto_33
    :try_start_33
    new-instance v0, Lhn/hato/ganadero/Enlace$Recibido;

    invoke-direct {v0}, Lhn/hato/ganadero/Enlace$Recibido;-><init>()V

    const-string v1, "Recibido"

    invoke-virtual {p1, v0, v1}, Landroid/webkit/WebView;->addJavascriptInterface(Ljava/lang/Object;Ljava/lang/String;)V
    :try_end_3d
    .catch Ljava/lang/Throwable; {:try_start_33 .. :try_end_3d} :catch_3e

    .line 42
    :goto_3d
    return-void

    .line 40
    :catch_3e
    move-exception v0

    goto :goto_3d

    .line 36
    :catch_40
    move-exception v0

    goto :goto_33

    .line 31
    :catch_42
    move-exception v0

    goto :goto_29
.end method

.method public static verificarRsa(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z
    .registers 7

    .prologue
    const/4 v0, 0x0

    .line 93
    const/4 v1, 0x0

    :try_start_2
    invoke-static {p0, v1}, Landroid/util/Base64;->decode(Ljava/lang/String;I)[B

    move-result-object v1

    .line 94
    const-string v2, "RSA"

    invoke-static {v2}, Ljava/security/KeyFactory;->getInstance(Ljava/lang/String;)Ljava/security/KeyFactory;

    move-result-object v2

    new-instance v3, Ljava/security/spec/X509EncodedKeySpec;

    invoke-direct {v3, v1}, Ljava/security/spec/X509EncodedKeySpec;-><init>([B)V

    invoke-virtual {v2, v3}, Ljava/security/KeyFactory;->generatePublic(Ljava/security/spec/KeySpec;)Ljava/security/PublicKey;

    move-result-object v1

    .line 95
    const-string v2, "SHA1withRSA"

    invoke-static {v2}, Ljava/security/Signature;->getInstance(Ljava/lang/String;)Ljava/security/Signature;

    move-result-object v2

    .line 96
    invoke-virtual {v2, v1}, Ljava/security/Signature;->initVerify(Ljava/security/PublicKey;)V

    .line 97
    const-string v1, "UTF-8"

    invoke-virtual {p1, v1}, Ljava/lang/String;->getBytes(Ljava/lang/String;)[B

    move-result-object v1

    invoke-virtual {v2, v1}, Ljava/security/Signature;->update([B)V

    .line 98
    const/4 v1, 0x0

    invoke-static {p2, v1}, Landroid/util/Base64;->decode(Ljava/lang/String;I)[B

    move-result-object v1

    invoke-virtual {v2, v1}, Ljava/security/Signature;->verify([B)Z
    :try_end_2f
    .catch Ljava/lang/Throwable; {:try_start_2 .. :try_end_2f} :catch_31

    move-result v0

    .line 100
    :goto_30
    return v0

    .line 99
    :catch_31
    move-exception v1

    goto :goto_30
.end method
