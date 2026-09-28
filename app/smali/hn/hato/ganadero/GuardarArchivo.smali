.class public Lhn/hato/ganadero/GuardarArchivo;
.super Landroid/app/Activity;
.source "GuardarArchivo.java"


# static fields
.field private static final PEDIR:I = 0x29


# instance fields
.field private nombre:Ljava/lang/String;


# direct methods
.method public constructor <init>()V
    .registers 1

    .prologue
    .line 16
    invoke-direct {p0}, Landroid/app/Activity;-><init>()V

    return-void
.end method


# virtual methods
.method protected onActivityResult(IILandroid/content/Intent;)V
    .registers 10

    .prologue
    const/4 v1, 0x0

    const/4 v3, 0x0

    .line 42
    invoke-super {p0, p1, p2, p3}, Landroid/app/Activity;->onActivityResult(IILandroid/content/Intent;)V

    .line 43
    const/16 v0, 0x29

    if-ne p1, v0, :cond_62

    const/4 v0, -0x1

    if-ne p2, v0, :cond_62

    if-eqz p3, :cond_62

    invoke-virtual {p3}, Landroid/content/Intent;->getData()Landroid/net/Uri;

    move-result-object v0

    if-eqz v0, :cond_62

    iget-object v0, p0, Lhn/hato/ganadero/GuardarArchivo;->nombre:Ljava/lang/String;

    if-eqz v0, :cond_62

    .line 48
    :try_start_18
    new-instance v2, Ljava/io/FileInputStream;

    new-instance v0, Ljava/io/File;

    invoke-static {p0}, Lhn/hato/ganadero/Archivos;->carpeta(Landroid/content/Context;)Ljava/io/File;

    move-result-object v4

    iget-object v5, p0, Lhn/hato/ganadero/GuardarArchivo;->nombre:Ljava/lang/String;

    invoke-static {v5}, Lhn/hato/ganadero/Archivos;->limpiar(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v5

    invoke-direct {v0, v4, v5}, Ljava/io/File;-><init>(Ljava/io/File;Ljava/lang/String;)V

    invoke-direct {v2, v0}, Ljava/io/FileInputStream;-><init>(Ljava/io/File;)V
    :try_end_2c
    .catch Ljava/lang/Exception; {:try_start_18 .. :try_end_2c} :catch_9b
    .catchall {:try_start_18 .. :try_end_2c} :catchall_79

    .line 49
    :try_start_2c
    invoke-virtual {p0}, Lhn/hato/ganadero/GuardarArchivo;->getContentResolver()Landroid/content/ContentResolver;

    move-result-object v0

    invoke-virtual {p3}, Landroid/content/Intent;->getData()Landroid/net/Uri;

    move-result-object v4

    const-string v5, "w"

    invoke-virtual {v0, v4, v5}, Landroid/content/ContentResolver;->openOutputStream(Landroid/net/Uri;Ljava/lang/String;)Ljava/io/OutputStream;
    :try_end_39
    .catch Ljava/lang/Exception; {:try_start_2c .. :try_end_39} :catch_9f
    .catchall {:try_start_2c .. :try_end_39} :catchall_93

    move-result-object v0

    .line 50
    if-eqz v0, :cond_a2

    .line 51
    const/16 v1, 0x4000

    :try_start_3e
    new-array v1, v1, [B

    .line 53
    :goto_40
    invoke-virtual {v2, v1}, Ljava/io/InputStream;->read([B)I

    move-result v4

    if-lez v4, :cond_66

    const/4 v5, 0x0

    invoke-virtual {v0, v1, v5, v4}, Ljava/io/OutputStream;->write([BII)V
    :try_end_4a
    .catch Ljava/lang/Exception; {:try_start_3e .. :try_end_4a} :catch_4b
    .catchall {:try_start_3e .. :try_end_4a} :catchall_97

    goto :goto_40

    .line 56
    :catch_4b
    move-exception v1

    .line 59
    :goto_4c
    if-eqz v2, :cond_51

    :try_start_4e
    invoke-virtual {v2}, Ljava/io/InputStream;->close()V
    :try_end_51
    .catch Ljava/lang/Exception; {:try_start_4e .. :try_end_51} :catch_8d

    .line 60
    :cond_51
    :goto_51
    if-eqz v0, :cond_56

    :try_start_53
    invoke-virtual {v0}, Ljava/io/OutputStream;->close()V
    :try_end_56
    .catch Ljava/lang/Exception; {:try_start_53 .. :try_end_56} :catch_76

    :cond_56
    move v0, v3

    .line 62
    :goto_57
    if-eqz v0, :cond_88

    const-string v0, "Archivo guardado"

    :goto_5b
    invoke-static {p0, v0, v3}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;

    move-result-object v0

    invoke-virtual {v0}, Landroid/widget/Toast;->show()V

    .line 64
    :cond_62
    invoke-virtual {p0}, Lhn/hato/ganadero/GuardarArchivo;->finish()V

    .line 65
    return-void

    .line 54
    :cond_66
    const/4 v1, 0x1

    .line 59
    :goto_67
    if-eqz v2, :cond_6c

    :try_start_69
    invoke-virtual {v2}, Ljava/io/InputStream;->close()V
    :try_end_6c
    .catch Ljava/lang/Exception; {:try_start_69 .. :try_end_6c} :catch_8b

    .line 60
    :cond_6c
    :goto_6c
    if-eqz v0, :cond_71

    :try_start_6e
    invoke-virtual {v0}, Ljava/io/OutputStream;->close()V
    :try_end_71
    .catch Ljava/lang/Exception; {:try_start_6e .. :try_end_71} :catch_73

    :cond_71
    move v0, v1

    goto :goto_57

    :catch_73
    move-exception v0

    move v0, v1

    .line 61
    goto :goto_57

    .line 60
    :catch_76
    move-exception v0

    move v0, v3

    .line 61
    goto :goto_57

    .line 59
    :catchall_79
    move-exception v0

    move-object v3, v0

    move-object v4, v1

    move-object v2, v1

    :goto_7d
    if-eqz v2, :cond_82

    :try_start_7f
    invoke-virtual {v2}, Ljava/io/InputStream;->close()V
    :try_end_82
    .catch Ljava/lang/Exception; {:try_start_7f .. :try_end_82} :catch_8f

    .line 60
    :cond_82
    :goto_82
    if-eqz v4, :cond_87

    :try_start_84
    invoke-virtual {v4}, Ljava/io/OutputStream;->close()V
    :try_end_87
    .catch Ljava/lang/Exception; {:try_start_84 .. :try_end_87} :catch_91

    .line 61
    :cond_87
    :goto_87
    throw v3

    .line 62
    :cond_88
    const-string v0, "No se pudo guardar el archivo"

    goto :goto_5b

    .line 59
    :catch_8b
    move-exception v2

    goto :goto_6c

    :catch_8d
    move-exception v1

    goto :goto_51

    :catch_8f
    move-exception v0

    goto :goto_82

    .line 60
    :catch_91
    move-exception v0

    goto :goto_87

    .line 59
    :catchall_93
    move-exception v0

    move-object v3, v0

    move-object v4, v1

    goto :goto_7d

    :catchall_97
    move-exception v1

    move-object v3, v1

    move-object v4, v0

    goto :goto_7d

    .line 56
    :catch_9b
    move-exception v0

    move-object v0, v1

    move-object v2, v1

    goto :goto_4c

    :catch_9f
    move-exception v0

    move-object v0, v1

    goto :goto_4c

    :cond_a2
    move v1, v3

    goto :goto_67
.end method

.method protected onCreate(Landroid/os/Bundle;)V
    .registers 5

    .prologue
    .line 21
    invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V

    .line 22
    invoke-virtual {p0}, Lhn/hato/ganadero/GuardarArchivo;->getIntent()Landroid/content/Intent;

    move-result-object v0

    const-string v1, "nombre"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    iput-object v0, p0, Lhn/hato/ganadero/GuardarArchivo;->nombre:Ljava/lang/String;

    .line 23
    if-eqz p1, :cond_12

    .line 34
    :goto_11
    return-void

    .line 25
    :cond_12
    :try_start_12
    new-instance v0, Landroid/content/Intent;

    const-string v1, "android.intent.action.CREATE_DOCUMENT"

    invoke-direct {v0, v1}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 26
    const-string v1, "android.intent.category.OPENABLE"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->addCategory(Ljava/lang/String;)Landroid/content/Intent;

    .line 27
    invoke-virtual {p0}, Lhn/hato/ganadero/GuardarArchivo;->getIntent()Landroid/content/Intent;

    move-result-object v1

    const-string v2, "mime"

    invoke-virtual {v1, v2}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setType(Ljava/lang/String;)Landroid/content/Intent;

    .line 28
    const-string v1, "android.intent.extra.TITLE"

    iget-object v2, p0, Lhn/hato/ganadero/GuardarArchivo;->nombre:Ljava/lang/String;

    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 29
    const/16 v1, 0x29

    invoke-virtual {p0, v0, v1}, Lhn/hato/ganadero/GuardarArchivo;->startActivityForResult(Landroid/content/Intent;I)V
    :try_end_37
    .catch Ljava/lang/Exception; {:try_start_12 .. :try_end_37} :catch_38

    goto :goto_11

    .line 30
    :catch_38
    move-exception v0

    .line 31
    const-string v0, "Este tel\u00e9fono no permite elegir d\u00f3nde guardar."

    const/4 v1, 0x1

    invoke-static {p0, v0, v1}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;

    move-result-object v0

    invoke-virtual {v0}, Landroid/widget/Toast;->show()V

    .line 32
    invoke-virtual {p0}, Lhn/hato/ganadero/GuardarArchivo;->finish()V

    goto :goto_11
.end method

.method protected onSaveInstanceState(Landroid/os/Bundle;)V
    .registers 4

    .prologue
    .line 37
    invoke-super {p0, p1}, Landroid/app/Activity;->onSaveInstanceState(Landroid/os/Bundle;)V

    .line 38
    const-string v0, "nombre"

    iget-object v1, p0, Lhn/hato/ganadero/GuardarArchivo;->nombre:Ljava/lang/String;

    invoke-virtual {p1, v0, v1}, Landroid/os/Bundle;->putString(Ljava/lang/String;Ljava/lang/String;)V

    .line 39
    return-void
.end method
