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
    .locals 0

    .prologue
    .line 103
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 104
    iput-object p1, p0, Lhn/hato/ganadero/Enlace$Recibido;->a:Landroid/app/Activity;

    .line 105
    return-void
.end method


# virtual methods
.method public pasar(Ljava/lang/String;)Z
    .locals 6
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .prologue
    const/4 v0, 0x0

    .line 111
    :try_start_0
    iget-object v1, p0, Lhn/hato/ganadero/Enlace$Recibido;->a:Landroid/app/Activity;

    invoke-virtual {v1}, Landroid/app/Activity;->getPackageName()Ljava/lang/String;

    move-result-object v1

    invoke-static {v1}, Lhn/hato/ganadero/Enlace;->otraApp(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v2

    .line 112
    if-nez v2, :cond_1

    .line 125
    :cond_0
    :goto_0
    return v0

    .line 113
    :cond_1
    const-string v1, ".vaquero"

    invoke-virtual {v2, v1}, Ljava/lang/String;->contains(Ljava/lang/CharSequence;)Z

    move-result v1

    .line 114
    iget-object v3, p0, Lhn/hato/ganadero/Enlace$Recibido;->a:Landroid/app/Activity;

    if-eqz v1, :cond_2

    const-string v1, "equipo.campo"

    :goto_1
    invoke-static {v3, v1, p1}, Lhn/hato/ganadero/Archivos;->escribir(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    .line 115
    if-eqz v1, :cond_0

    .line 116
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

    .line 117
    new-instance v4, Landroid/content/Intent;

    const-string v5, "android.intent.action.VIEW"

    invoke-direct {v4, v5}, Landroid/content/Intent;-><init>(Ljava/lang/String;)V

    .line 118
    const/4 v5, 0x0

    invoke-static {v1, v5}, Lhn/hato/ganadero/Archivos;->tipo(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-virtual {v4, v3, v1}, Landroid/content/Intent;->setDataAndType(Landroid/net/Uri;Ljava/lang/String;)Landroid/content/Intent;

    .line 119
    invoke-virtual {v4, v2}, Landroid/content/Intent;->setPackage(Ljava/lang/String;)Landroid/content/Intent;

    .line 120
    const v1, 0x10000001

    invoke-virtual {v4, v1}, Landroid/content/Intent;->addFlags(I)Landroid/content/Intent;

    .line 121
    iget-object v1, p0, Lhn/hato/ganadero/Enlace$Recibido;->a:Landroid/app/Activity;

    invoke-virtual {v1}, Landroid/app/Activity;->getPackageManager()Landroid/content/pm/PackageManager;

    move-result-object v1

    invoke-virtual {v4, v1}, Landroid/content/Intent;->resolveActivity(Landroid/content/pm/PackageManager;)Landroid/content/ComponentName;

    move-result-object v1

    if-eqz v1, :cond_0

    .line 122
    iget-object v1, p0, Lhn/hato/ganadero/Enlace$Recibido;->a:Landroid/app/Activity;

    invoke-virtual {v1, v4}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V

    .line 123
    const/4 v0, 0x1

    goto :goto_0

    .line 114
    :cond_2
    const-string v1, "equipo.rumentis"
    :try_end_0
    .catch Ljava/lang/Throwable; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_1

    .line 124
    :catch_0
    move-exception v1

    goto :goto_0
.end method

.method public tomar()Ljava/lang/String;
    .locals 2
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .prologue
    .line 131
    invoke-static {}, Lhn/hato/ganadero/Enlace;->access$000()Ljava/lang/String;

    move-result-object v0

    .line 132
    const/4 v1, 0x0

    invoke-static {v1}, Lhn/hato/ganadero/Enlace;->access$002(Ljava/lang/String;)Ljava/lang/String;

    .line 133
    if-nez v0, :cond_0

    const-string v0, ""

    :cond_0
    return-object v0
.end method
