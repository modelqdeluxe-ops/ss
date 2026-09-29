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


# direct methods
.method public constructor <init>()V
    .registers 1

    .prologue
    .line 82
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public tomar()Ljava/lang/String;
    .registers 3
    .annotation runtime Landroid/webkit/JavascriptInterface;
    .end annotation

    .prologue
    .line 85
    # getter for: Lhn/hato/ganadero/Enlace;->recibido:Ljava/lang/String;
    invoke-static {}, Lhn/hato/ganadero/Enlace;->access$000()Ljava/lang/String;

    move-result-object v0

    .line 86
    const/4 v1, 0x0

    # setter for: Lhn/hato/ganadero/Enlace;->recibido:Ljava/lang/String;
    invoke-static {v1}, Lhn/hato/ganadero/Enlace;->access$002(Ljava/lang/String;)Ljava/lang/String;

    .line 87
    if-nez v0, :cond_c

    const-string v0, ""

    :cond_c
    return-object v0
.end method
