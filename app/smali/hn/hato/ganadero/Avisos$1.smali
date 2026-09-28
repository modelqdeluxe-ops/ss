.class Lhn/hato/ganadero/Avisos$1;
.super Ljava/lang/Object;
.source "Avisos.java"

# interfaces
.implements Ljava/lang/Runnable;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lhn/hato/ganadero/Avisos;->deIntent(Landroid/app/Activity;Landroid/webkit/WebView;)V
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic val$w:Landroid/webkit/WebView;


# direct methods
.method constructor <init>(Landroid/webkit/WebView;)V
    .locals 0
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()V"
        }
    .end annotation

    .prologue
    .line 167
    iput-object p1, p0, Lhn/hato/ganadero/Avisos$1;->val$w:Landroid/webkit/WebView;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .locals 3

    .prologue
    .line 167
    :try_start_0
    iget-object v0, p0, Lhn/hato/ganadero/Avisos$1;->val$w:Landroid/webkit/WebView;

    const-string v1, "window.avisoIr&&avisoIr()"

    const/4 v2, 0x0

    invoke-virtual {v0, v1, v2}, Landroid/webkit/WebView;->evaluateJavascript(Ljava/lang/String;Landroid/webkit/ValueCallback;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    :goto_0
    return-void

    :catch_0
    move-exception v0

    goto :goto_0
.end method
