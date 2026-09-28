.class public final Lhn/hato/ganadero/AvisoReceiver;
.super Landroid/content/BroadcastReceiver;
.source "AvisoReceiver.java"


# direct methods
.method public constructor <init>()V
    .locals 0

    .prologue
    .line 8
    invoke-direct {p0}, Landroid/content/BroadcastReceiver;-><init>()V

    return-void
.end method


# virtual methods
.method public onReceive(Landroid/content/Context;Landroid/content/Intent;)V
    .locals 2

    .prologue
    .line 10
    if-nez p2, :cond_1

    const/4 v0, 0x0

    .line 11
    :goto_0
    const-string v1, "android.intent.action.BOOT_COMPLETED"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_0

    const-string v1, "android.intent.action.MY_PACKAGE_REPLACED"

    invoke-virtual {v1, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-eqz v0, :cond_2

    :cond_0
    invoke-static {p1}, Lhn/hato/ganadero/Avisos;->programar(Landroid/content/Context;)V

    .line 13
    :goto_1
    return-void

    .line 10
    :cond_1
    invoke-virtual {p2}, Landroid/content/Intent;->getAction()Ljava/lang/String;

    move-result-object v0

    goto :goto_0

    .line 12
    :cond_2
    invoke-static {p1}, Lhn/hato/ganadero/Avisos;->revisar(Landroid/content/Context;)V

    goto :goto_1
.end method
