.class public final Lhn/hato/ganadero/RumiWidget;
.super Landroid/appwidget/AppWidgetProvider;
.source "RumiWidget.java"


# direct methods
.method public constructor <init>()V
    .locals 0

    .prologue
    .line 12
    invoke-direct {p0}, Landroid/appwidget/AppWidgetProvider;-><init>()V

    return-void
.end method

.method private static abrir(Landroid/content/Context;Ljava/lang/String;I)Landroid/app/PendingIntent;
    .locals 3

    .prologue
    .line 18
    new-instance v0, Landroid/content/Intent;

    const-class v1, Lhn/hato/ganadero/MainActivity;

    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    .line 19
    const-string v1, "ir"

    invoke-virtual {v0, v1, p1}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 20
    const/high16 v1, 0x14000000

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;

    .line 21
    add-int/lit8 v1, p2, 0x64

    const/high16 v2, 0xc000000

    invoke-static {p0, v1, v0, v2}, Landroid/app/PendingIntent;->getActivity(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;

    move-result-object v0

    return-object v0
.end method

.method private static id(Landroid/content/Context;Ljava/lang/String;)I
    .locals 3

    .prologue
    .line 15
    invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    const-string v1, "id"

    invoke-virtual {p0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, p1, v1, v2}, Landroid/content/res/Resources;->getIdentifier(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I

    move-result v0

    return v0
.end method

.method static pintar(Landroid/content/Context;Landroid/appwidget/AppWidgetManager;[I)V
    .locals 10

    .prologue
    const/4 v2, 0x0

    .line 25
    invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    const-string v1, "rumi_widget"

    const-string v3, "layout"

    invoke-virtual {p0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;

    move-result-object v4

    invoke-virtual {v0, v1, v3, v4}, Landroid/content/res/Resources;->getIdentifier(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I

    move-result v3

    .line 26
    if-nez v3, :cond_1

    .line 43
    :cond_0
    return-void

    .line 28
    :cond_1
    :try_start_0
    new-instance v0, Lorg/json/JSONObject;

    const-string v1, "rumi_widget"

    const/4 v4, 0x0

    invoke-virtual {p0, v1, v4}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;

    move-result-object v1

    const-string v4, "datos"

    const-string v5, "{}"

    invoke-interface {v1, v4, v5}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    invoke-direct {v0, v1}, Lorg/json/JSONObject;-><init>(Ljava/lang/String;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    .line 29
    :goto_0
    array-length v4, p2

    move v1, v2

    :goto_1
    if-ge v1, v4, :cond_0

    aget v5, p2, v1

    .line 30
    new-instance v6, Landroid/widget/RemoteViews;

    invoke-virtual {p0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;

    move-result-object v7

    invoke-direct {v6, v7, v3}, Landroid/widget/RemoteViews;-><init>(Ljava/lang/String;I)V

    .line 31
    const-string v7, "w_titulo"

    invoke-static {p0, v7}, Lhn/hato/ganadero/RumiWidget;->id(Landroid/content/Context;Ljava/lang/String;)I

    move-result v7

    const-string v8, "finca"

    const-string v9, "Rumentis"

    invoke-virtual {v0, v8, v9}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v8

    invoke-virtual {v6, v7, v8}, Landroid/widget/RemoteViews;->setTextViewText(ILjava/lang/CharSequence;)V

    .line 32
    const-string v7, "w_grande"

    invoke-static {p0, v7}, Lhn/hato/ganadero/RumiWidget;->id(Landroid/content/Context;Ljava/lang/String;)I

    move-result v7

    const-string v8, "grande"

    const-string v9, "Abre Rumentis"

    invoke-virtual {v0, v8, v9}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v8

    invoke-virtual {v6, v7, v8}, Landroid/widget/RemoteViews;->setTextViewText(ILjava/lang/CharSequence;)V

    .line 33
    const-string v7, "w_sub"

    invoke-static {p0, v7}, Lhn/hato/ganadero/RumiWidget;->id(Landroid/content/Context;Ljava/lang/String;)I

    move-result v7

    const-string v8, "sub"

    const-string v9, ""

    invoke-virtual {v0, v8, v9}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v8

    invoke-virtual {v6, v7, v8}, Landroid/widget/RemoteViews;->setTextViewText(ILjava/lang/CharSequence;)V

    .line 34
    const-string v7, "w_tareas"

    invoke-static {p0, v7}, Lhn/hato/ganadero/RumiWidget;->id(Landroid/content/Context;Ljava/lang/String;)I

    move-result v7

    const-string v8, "tareas"

    const-string v9, ""

    invoke-virtual {v0, v8, v9}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v8

    invoke-virtual {v6, v7, v8}, Landroid/widget/RemoteViews;->setTextViewText(ILjava/lang/CharSequence;)V

    .line 35
    const-string v7, "w_rumi"

    invoke-static {p0, v7}, Lhn/hato/ganadero/RumiWidget;->id(Landroid/content/Context;Ljava/lang/String;)I

    move-result v7

    const-string v8, "rumi"

    const-string v9, ""

    invoke-virtual {v0, v8, v9}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v8

    invoke-virtual {v6, v7, v8}, Landroid/widget/RemoteViews;->setTextViewText(ILjava/lang/CharSequence;)V

    .line 36
    const-string v7, "w_act"

    invoke-static {p0, v7}, Lhn/hato/ganadero/RumiWidget;->id(Landroid/content/Context;Ljava/lang/String;)I

    move-result v7

    const-string v8, "act"

    const-string v9, ""

    invoke-virtual {v0, v8, v9}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v8

    invoke-virtual {v6, v7, v8}, Landroid/widget/RemoteViews;->setTextViewText(ILjava/lang/CharSequence;)V

    .line 37
    const-string v7, "w_anotar"

    invoke-static {p0, v7}, Lhn/hato/ganadero/RumiWidget;->id(Landroid/content/Context;Ljava/lang/String;)I

    move-result v7

    const-string v8, "anotar"

    const-string v9, "+ Anotar"

    invoke-virtual {v0, v8, v9}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v8

    invoke-virtual {v6, v7, v8}, Landroid/widget/RemoteViews;->setTextViewText(ILjava/lang/CharSequence;)V

    .line 38
    const-string v7, "w_raiz"

    invoke-static {p0, v7}, Lhn/hato/ganadero/RumiWidget;->id(Landroid/content/Context;Ljava/lang/String;)I

    move-result v7

    const-string v8, "#hoy"

    invoke-static {p0, v8, v2}, Lhn/hato/ganadero/RumiWidget;->abrir(Landroid/content/Context;Ljava/lang/String;I)Landroid/app/PendingIntent;

    move-result-object v8

    invoke-virtual {v6, v7, v8}, Landroid/widget/RemoteViews;->setOnClickPendingIntent(ILandroid/app/PendingIntent;)V

    .line 39
    const-string v7, "w_anotar"

    invoke-static {p0, v7}, Lhn/hato/ganadero/RumiWidget;->id(Landroid/content/Context;Ljava/lang/String;)I

    move-result v7

    const-string v8, "#registrar"

    const/4 v9, 0x1

    invoke-static {p0, v8, v9}, Lhn/hato/ganadero/RumiWidget;->abrir(Landroid/content/Context;Ljava/lang/String;I)Landroid/app/PendingIntent;

    move-result-object v8

    invoke-virtual {v6, v7, v8}, Landroid/widget/RemoteViews;->setOnClickPendingIntent(ILandroid/app/PendingIntent;)V

    .line 40
    const-string v7, "w_rumi"

    invoke-static {p0, v7}, Lhn/hato/ganadero/RumiWidget;->id(Landroid/content/Context;Ljava/lang/String;)I

    move-result v7

    const-string v8, "rumiIr"

    const-string v9, "#analisis"

    invoke-virtual {v0, v8, v9}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v8

    const/4 v9, 0x2

    invoke-static {p0, v8, v9}, Lhn/hato/ganadero/RumiWidget;->abrir(Landroid/content/Context;Ljava/lang/String;I)Landroid/app/PendingIntent;

    move-result-object v8

    invoke-virtual {v6, v7, v8}, Landroid/widget/RemoteViews;->setOnClickPendingIntent(ILandroid/app/PendingIntent;)V

    .line 41
    :try_start_1
    invoke-virtual {p1, v5, v6}, Landroid/appwidget/AppWidgetManager;->updateAppWidget(ILandroid/widget/RemoteViews;)V
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    .line 29
    :goto_2
    add-int/lit8 v1, v1, 0x1

    goto/16 :goto_1

    .line 28
    :catch_0
    move-exception v0

    new-instance v0, Lorg/json/JSONObject;

    invoke-direct {v0}, Lorg/json/JSONObject;-><init>()V

    goto/16 :goto_0

    .line 41
    :catch_1
    move-exception v5

    goto :goto_2
.end method


# virtual methods
.method public onUpdate(Landroid/content/Context;Landroid/appwidget/AppWidgetManager;[I)V
    .locals 0

    .prologue
    .line 13
    invoke-static {p1, p2, p3}, Lhn/hato/ganadero/RumiWidget;->pintar(Landroid/content/Context;Landroid/appwidget/AppWidgetManager;[I)V

    return-void
.end method
