.class public Lhn/hato/ganadero/Enlace$Recibido;
.super Ljava/lang/Object;
.source "Enlace.java"


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lhn/hato/ganadero/Enlace;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x9
    name = "Recibido"
.end annotation


# instance fields
.field private final a:Landroid/app/Activity;


# direct methods
.method constructor <init>(Landroid/app/Activity;)V
    .registers 2

    .prologue
    .line 94
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 95
    iput-object p1, p0, Lhn/hato/ganadero/Enlace$Recibido;->a:Landroid/app/Activity;

    .line 96
    return-void
.end method


# virtual methods
.method public pasar(Ljava/lang/String;)Z
    .registers 8
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .prologue
    const/4 v0, 0x0

    .line 102
    :try_start_1
    iget-object v1, p0, Lhn/hato/ganadero/Enlace$Recibido;->a:Landroid/app/Activity;

    invoke-virtual {v1}, Landroid/app/Activity;->getPackageName()Ljava/lang/String;

    move-result-object v1

    invoke-static {v1}, Lhn/hato/ganadero/Enlace;->otraApp(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v2

    .line 103
    if-nez v2, :cond_e

    .line 116
    :cond_d
    :goto_d
    return v0

    .line 104
    :cond_e
    const-string v1, ".vaquero"

    invoke-virtual {v2, v1}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result v1

    .line 105
    iget-object v3, p0, Lhn/hato/ganadero/Enlace$Recibido;->a:Landroid/app/Activity;

    if-eqz v1, :cond_76

    const-string v1, "equipo.campo"

    :goto_1a
    invoke-static {v3, v1, p1}, Lhn/hato/ganadero/Archivos;->escribir(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    .line 106
    if-eqz v1, :cond_d

    .line 107
    new-instance v3, Ljava/lang/StringBuilder;

    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "content://"

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    iget-object v4, p0, Lhn/hato/ganadero/Enlace$Recibido;->a:Landroid/app/Activity;

    invoke-virtual {v4}, Landroid/app/Activity;->getPackageName()Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    const-string v4, ".archivos/"

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-static {v1}, Landroid/net/Uri;->encode(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v3

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object v3

    invoke-static {v3}, Landroid/net/Uri;->parse(Ljava/lang/String;)Landroid/net/Uri;

    move-result-object v3

    .line 108
    new-instance v4, Landroid/content/Intent;

    const-string v5, "android.intent.action.VIEW"

    invoke-direct {v4, v5}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 109
    const/4 v5, 0x0

    invoke-static {v1, v5}, Lhn/hato/ganadero/Archivos;->tipo(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v4, v3, v1}, Landroid/content/Intent;->setDataAndType(Landroid/net/Uri;Ljava/lang/String;)Landroid/content/Intent;

    .line 110
    invoke-virtual {v4, v2}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    .line 111
    const v1, 0x10000001

    invoke-virtual {v4, v1}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 112
    iget-object v1, p0, Lhn/hato/ganadero/Enlace$Recibido;->a:Landroid/app/Activity;

    invoke-virtual {v1}, Landroid/app/Activity;->getPackageManager()Landroid/content/pm/PackageManager;

    move-result-object v1

    invoke-virtual {v4, v1}, Landroid/content/Intent;->resolveActivity(Landroid/content/pm/PackageManager;)Landroid/content/ComponentName;

    move-result-object v1

    if-eqz v1, :cond_d

    .line 113
    iget-object v1, p0, Lhn/hato/ganadero/Enlace$Recibido;->a:Landroid/app/Activity;

    invoke-virtual {v1, v4}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V

    .line 114
    const/4 v0, 0x1

    goto :goto_d

    .line 105
    :cond_76
    const-string v1, "equipo.rumentis"
    :try_end_78
    .catch Ljava/lang/Throwable; {:try_start_1 .. :try_end_78} :catch_79

    goto :goto_1a

    .line 115
    :catch_79
    move-exception v1

    goto :goto_d
.end method

.method public tomar()Ljava/lang/String;
    .registers 3
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .prologue
    .line 122
    # getter for: Lhn/hato/ganadero/Enlace;->recibido:Ljava/lang/String;
    invoke-static {}, Lhn/hato/ganadero/Enlace;->access$000()Ljava/lang/String;

    move-result-object v0

    .line 123
    const/4 v1, 0x0

    # setter for: Lhn/hato/ganadero/Enlace;->recibido:Ljava/lang/String;
    invoke-static {v1}, Lhn/hato/ganadero/Enlace;->access$002(Ljava/lang/String;)Ljava/lang/String;

    .line 124
    if-nez v0, :cond_c

    const-string v0, ""

    :cond_c
    return-object v0
.end method
