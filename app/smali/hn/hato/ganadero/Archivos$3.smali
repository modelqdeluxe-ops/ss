.class Lhn/hato/ganadero/Archivos$3;
.super Ljava/lang/Object;
.source "Archivos.java"

# interfaces
.implements Ljava/lang/Runnable;


# annotations
.annotation system Ldalvik/annotation/EnclosingMethod;
    value = Lhn/hato/ganadero/Archivos;->guardar(Landroid/app/Activity;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x0
    name = null
.end annotation


# instance fields
.field final synthetic val$a:Landroid/app/Activity;

.field final synthetic val$mime:Ljava/lang/String;

.field final synthetic val$n:Ljava/lang/String;


# direct methods
.method constructor <init>(Landroid/app/Activity;Ljava/lang/String;Ljava/lang/String;)V
    .registers 4
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "()V"
        }
    .end annotation

    .prologue
    .line 126
    iput-object p1, p0, Lhn/hato/ganadero/Archivos$3;->val$a:Landroid/app/Activity;

    iput-object p2, p0, Lhn/hato/ganadero/Archivos$3;->val$n:Ljava/lang/String;

    iput-object p3, p0, Lhn/hato/ganadero/Archivos$3;->val$mime:Ljava/lang/String;

    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method


# virtual methods
.method public run()V
    .registers 5

    .prologue
    .line 129
    :try_start_0
    new-instance v0, Landroid/content/Intent;

    iget-object v1, p0, Lhn/hato/ganadero/Archivos$3;->val$a:Landroid/app/Activity;

    const-class v2, Lhn/hato/ganadero/GuardarArchivo;

    invoke-direct {v0, v1, v2}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    .line 130
    const-string v1, "nombre"

    iget-object v2, p0, Lhn/hato/ganadero/Archivos$3;->val$n:Ljava/lang/String;

    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 131
    const-string v1, "mime"

    iget-object v2, p0, Lhn/hato/ganadero/Archivos$3;->val$n:Ljava/lang/String;

    iget-object v3, p0, Lhn/hato/ganadero/Archivos$3;->val$mime:Ljava/lang/String;

    invoke-static {v2, v3}, Lhn/hato/ganadero/Archivos;->tipo(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 132
    iget-object v1, p0, Lhn/hato/ganadero/Archivos$3;->val$a:Landroid/app/Activity;

    invoke-virtual {v1, v0}, Landroid/app/Activity;->startActivity(Landroid/content/Intent;)V
    :try_end_22
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_22} :catch_23

    .line 136
    :goto_22
    return-void

    .line 133
    :catch_23
    move-exception v0

    .line 134
    iget-object v0, p0, Lhn/hato/ganadero/Archivos$3;->val$a:Landroid/app/Activity;

    const-string v1, "No se pudo guardar el archivo."

    const/4 v2, 0x1

    invoke-static {v0, v1, v2}, Landroid/widget/Toast;->makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;

    move-result-object v0

    invoke-virtual {v0}, Landroid/widget/Toast;->show()V

    goto :goto_22
.end method
