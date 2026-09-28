.class public final Lhn/hato/ganadero/Avisos;
.super Ljava/lang/Object;
.source "Avisos.java"


# static fields
.field static final CANAL:Ljava/lang/String; = "rumi"

.field static final PREF:Ljava/lang/String; = "rumi_avisos"

.field static ir:Ljava/lang/String;


# direct methods
.method static constructor <clinit>()V
    .locals 1

    .prologue
    .line 32
    const/4 v0, 0x0

    sput-object v0, Lhn/hato/ganadero/Avisos;->ir:Ljava/lang/String;

    return-void
.end method

.method public constructor <init>()V
    .locals 0

    .prologue
    .line 29
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static activar(Landroid/content/Context;ZI)V
    .locals 3

    .prologue
    .line 44
    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->sp(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object v0

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v1, "activo"

    invoke-interface {v0, v1, p1}, Landroid/content/SharedPreferences$Editor;->putBoolean(Ljava/lang/String;Z)Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v1, "hora"

    if-ltz p2, :cond_0

    const/16 v2, 0x17

    if-le p2, v2, :cond_1

    :cond_0
    const/4 p2, 0x7

    :cond_1
    invoke-interface {v0, v1, p2}, Landroid/content/SharedPreferences$Editor;->putInt(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    .line 45
    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->programar(Landroid/content/Context;)V

    .line 46
    return-void
.end method

.method private static alarma(Landroid/content/Context;)Landroid/app/PendingIntent;
    .locals 3

    .prologue
    .line 68
    new-instance v0, Landroid/content/Intent;

    const-class v1, Lhn/hato/ganadero/AvisoReceiver;

    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    .line 69
    const-string v1, "hn.hato.ganadero.AVISO"

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setAction(Ljava/lang/String;)Landroid/content/Intent;

    .line 70
    const/4 v1, 0x1

    const/high16 v2, 0xc000000

    invoke-static {p0, v1, v0, v2}, Landroid/app/PendingIntent;->getBroadcast(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;

    move-result-object v0

    return-object v0
.end method

.method private static canal(Landroid/content/Context;)V
    .locals 5

    .prologue
    .line 89
    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x1a

    if-ge v0, v1, :cond_1

    .line 95
    :cond_0
    :goto_0
    return-void

    .line 90
    :cond_1
    const-string v0, "notification"

    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/app/NotificationManager;

    .line 91
    if-eqz v0, :cond_0

    const-string v1, "rumi"

    invoke-virtual {v0, v1}, Landroid/app/NotificationManager;->getNotificationChannel(Ljava/lang/String;)Landroid/app/NotificationChannel;

    move-result-object v1

    if-nez v1, :cond_0

    .line 92
    new-instance v1, Landroid/app/NotificationChannel;

    const-string v2, "rumi"

    const-string v3, "Avisos de Rumi"

    const/4 v4, 0x3

    invoke-direct {v1, v2, v3, v4}, Landroid/app/NotificationChannel;-><init>(Ljava/lang/String;Ljava/lang/CharSequence;I)V

    .line 93
    const-string v2, "Un aviso al d\u00eda, solo lo importante de tu engorde"

    invoke-virtual {v1, v2}, Landroid/app/NotificationChannel;->setDescription(Ljava/lang/String;)V

    .line 94
    invoke-virtual {v0, v1}, Landroid/app/NotificationManager;->createNotificationChannel(Landroid/app/NotificationChannel;)V

    goto :goto_0
.end method

.method public static deIntent(Landroid/app/Activity;Landroid/webkit/WebView;)V
    .locals 3

    .prologue
    .line 162
    :try_start_0
    invoke-virtual {p0}, Landroid/app/Activity;->getIntent()Landroid/content/Intent;

    move-result-object v1

    .line 163
    if-nez v1, :cond_1

    const/4 v0, 0x0

    .line 164
    :goto_0
    if-nez v0, :cond_2

    .line 169
    :cond_0
    :goto_1
    return-void

    .line 163
    :cond_1
    const-string v0, "ir"

    invoke-virtual {v1, v0}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    goto :goto_0

    .line 165
    :cond_2
    const-string v2, "ir"

    invoke-virtual {v1, v2}, Landroid/content/Intent;->removeExtra(Ljava/lang/String;)V

    .line 166
    sput-object v0, Lhn/hato/ganadero/Avisos;->ir:Ljava/lang/String;

    .line 167
    if-eqz p1, :cond_0

    new-instance v0, Lhn/hato/ganadero/Avisos$1;

    invoke-direct {v0, p1}, Lhn/hato/ganadero/Avisos$1;-><init>(Landroid/webkit/WebView;)V

    invoke-virtual {p1, v0}, Landroid/webkit/WebView;->post(Ljava/lang/Runnable;)Z
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_1

    .line 168
    :catch_0
    move-exception v0

    goto :goto_1
.end method

.method public static estado(Landroid/content/Context;)Ljava/lang/String;
    .locals 3

    .prologue
    .line 61
    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->sp(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object v0

    const-string v1, "activo"

    const/4 v2, 0x0

    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v0

    if-nez v0, :cond_0

    const-string v0, "no"

    .line 62
    :goto_0
    return-object v0

    :cond_0
    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->permiso(Landroid/content/Context;)Z

    move-result v0

    if-eqz v0, :cond_1

    const-string v0, "si"

    goto :goto_0

    :cond_1
    const-string v0, "sin-permiso"

    goto :goto_0
.end method

.method public static guardar(Landroid/content/Context;Ljava/lang/String;)V
    .locals 2

    .prologue
    .line 39
    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->sp(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object v0

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v1, "cola"

    if-nez p1, :cond_0

    const-string p1, "[]"

    :cond_0
    invoke-interface {v0, v1, p1}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    .line 40
    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->programar(Landroid/content/Context;)V

    .line 41
    return-void
.end method

.method public static hora(Landroid/content/Context;)I
    .locals 3

    .prologue
    .line 65
    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->sp(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object v0

    const-string v1, "hora"

    const/4 v2, 0x7

    invoke-interface {v0, v1, v2}, Landroid/content/SharedPreferences;->getInt(Ljava/lang/String;I)I

    move-result v0

    return v0
.end method

.method private static hoy()Ljava/lang/String;
    .locals 3

    .prologue
    .line 35
    new-instance v0, Ljava/text/SimpleDateFormat;

    const-string v1, "yyyy-MM-dd"

    sget-object v2, Ljava/util/Locale;->US:Ljava/util/Locale;

    invoke-direct {v0, v1, v2}, Ljava/text/SimpleDateFormat;-><init>(Ljava/lang/String;Ljava/util/Locale;)V

    new-instance v1, Ljava/util/Date;

    invoke-direct {v1}, Ljava/util/Date;-><init>()V

    invoke-virtual {v0, v1}, Ljava/text/SimpleDateFormat;->format(Ljava/util/Date;)Ljava/lang/String;

    move-result-object v0

    return-object v0
.end method

.method public static mostrar(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
    .locals 6

    .prologue
    .line 130
    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->permiso(Landroid/content/Context;)Z

    move-result v0

    if-nez v0, :cond_1

    .line 147
    :cond_0
    :goto_0
    return-void

    .line 131
    :cond_1
    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->canal(Landroid/content/Context;)V

    .line 132
    new-instance v0, Landroid/content/Intent;

    const-class v1, Lhn/hato/ganadero/MainActivity;

    invoke-direct {v0, p0, v1}, Landroid/content/Intent;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    .line 133
    const-string v1, "ir"

    if-nez p3, :cond_2

    const-string p3, "#hoy"

    :cond_2
    invoke-virtual {v0, v1, p3}, Landroid/content/Intent;->putExtra(Ljava/lang/String;Ljava/lang/String;)Landroid/content/Intent;

    .line 134
    const/high16 v1, 0x14000000

    invoke-virtual {v0, v1}, Landroid/content/Intent;->setFlags(I)Landroid/content/Intent;

    .line 135
    const/4 v1, 0x2

    const/high16 v2, 0xc000000

    invoke-static {p0, v1, v0, v2}, Landroid/app/PendingIntent;->getActivity(Landroid/content/Context;ILandroid/content/Intent;I)Landroid/app/PendingIntent;

    move-result-object v2

    .line 136
    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x1a

    if-lt v0, v1, :cond_4

    new-instance v0, Landroid/app/Notification$Builder;

    const-string v1, "rumi"

    invoke-direct {v0, p0, v1}, Landroid/app/Notification$Builder;-><init>(Landroid/content/Context;Ljava/lang/String;)V

    move-object v1, v0

    .line 137
    :goto_1
    invoke-virtual {p0}, Landroid/content/Context;->getResources()Landroid/content/res/Resources;

    move-result-object v0

    const-string v3, "ic_aviso"

    const-string v4, "drawable"

    invoke-virtual {p0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;

    move-result-object v5

    invoke-virtual {v0, v3, v4, v5}, Landroid/content/res/Resources;->getIdentifier(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I

    move-result v0

    .line 138
    if-eqz v0, :cond_5

    :goto_2
    invoke-virtual {v1, v0}, Landroid/app/Notification$Builder;->setSmallIcon(I)Landroid/app/Notification$Builder;

    move-result-object v0

    .line 139
    invoke-virtual {v0, p1}, Landroid/app/Notification$Builder;->setContentTitle(Ljava/lang/CharSequence;)Landroid/app/Notification$Builder;

    move-result-object v0

    .line 140
    invoke-virtual {v0, p2}, Landroid/app/Notification$Builder;->setContentText(Ljava/lang/CharSequence;)Landroid/app/Notification$Builder;

    move-result-object v0

    new-instance v3, Landroid/app/Notification$BigTextStyle;

    invoke-direct {v3}, Landroid/app/Notification$BigTextStyle;-><init>()V

    .line 141
    invoke-virtual {v3, p2}, Landroid/app/Notification$BigTextStyle;->bigText(Ljava/lang/CharSequence;)Landroid/app/Notification$BigTextStyle;

    move-result-object v3

    invoke-virtual {v0, v3}, Landroid/app/Notification$Builder;->setStyle(Landroid/app/Notification$Style;)Landroid/app/Notification$Builder;

    move-result-object v0

    .line 142
    invoke-virtual {v0, v2}, Landroid/app/Notification$Builder;->setContentIntent(Landroid/app/PendingIntent;)Landroid/app/Notification$Builder;

    move-result-object v0

    const/4 v2, 0x1

    .line 143
    invoke-virtual {v0, v2}, Landroid/app/Notification$Builder;->setAutoCancel(Z)Landroid/app/Notification$Builder;

    .line 144
    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v2, 0x15

    if-lt v0, v2, :cond_3

    const v0, -0xf40cd

    invoke-virtual {v1, v0}, Landroid/app/Notification$Builder;->setColor(I)Landroid/app/Notification$Builder;

    .line 145
    :cond_3
    const-string v0, "notification"

    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/app/NotificationManager;

    .line 146
    if-eqz v0, :cond_0

    const/4 v2, 0x7

    :try_start_0
    invoke-virtual {v1}, Landroid/app/Notification$Builder;->build()Landroid/app/Notification;

    move-result-object v1

    invoke-virtual {v0, v2, v1}, Landroid/app/NotificationManager;->notify(ILandroid/app/Notification;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v0

    goto/16 :goto_0

    .line 136
    :cond_4
    new-instance v0, Landroid/app/Notification$Builder;

    invoke-direct {v0, p0}, Landroid/app/Notification$Builder;-><init>(Landroid/content/Context;)V

    move-object v1, v0

    goto :goto_1

    .line 138
    :cond_5
    const v0, 0x108009b

    goto :goto_2
.end method

.method public static pedirPermiso(Landroid/app/Activity;)V
    .locals 3

    .prologue
    .line 54
    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x21

    if-lt v0, v1, :cond_0

    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->permiso(Landroid/content/Context;)Z

    move-result v0

    if-nez v0, :cond_0

    .line 55
    const/4 v0, 0x1

    :try_start_0
    new-array v0, v0, [Ljava/lang/String;

    const/4 v1, 0x0

    const-string v2, "android.permission.POST_NOTIFICATIONS"

    aput-object v2, v0, v1

    const/16 v1, 0x10

    invoke-virtual {p0, v0, v1}, Landroid/app/Activity;->requestPermissions([Ljava/lang/String;I)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    .line 57
    :cond_0
    :goto_0
    return-void

    .line 55
    :catch_0
    move-exception v0

    goto :goto_0
.end method

.method public static permiso(Landroid/content/Context;)Z
    .locals 3

    .prologue
    const/4 v0, 0x1

    .line 49
    sget v1, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v2, 0x21

    if-ge v1, v2, :cond_1

    .line 50
    :cond_0
    :goto_0
    return v0

    :cond_1
    const-string v1, "android.permission.POST_NOTIFICATIONS"

    invoke-virtual {p0, v1}, Landroid/content/Context;->checkSelfPermission(Ljava/lang/String;)I

    move-result v1

    if-eqz v1, :cond_0

    const/4 v0, 0x0

    goto :goto_0
.end method

.method public static programar(Landroid/content/Context;)V
    .locals 10

    .prologue
    const/4 v7, 0x5

    const/4 v4, 0x0

    .line 75
    const-string v0, "alarm"

    invoke-virtual {p0, v0}, Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Landroid/app/AlarmManager;

    .line 76
    if-nez v0, :cond_1

    .line 86
    :cond_0
    :goto_0
    return-void

    .line 77
    :cond_1
    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->alarma(Landroid/content/Context;)Landroid/app/PendingIntent;

    move-result-object v6

    .line 78
    invoke-virtual {v0, v6}, Landroid/app/AlarmManager;->cancel(Landroid/app/PendingIntent;)V

    .line 79
    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->sp(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object v1

    const-string v2, "activo"

    invoke-interface {v1, v2, v4}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v1

    if-eqz v1, :cond_0

    .line 80
    invoke-static {}, Ljava/util/Calendar;->getInstance()Ljava/util/Calendar;

    move-result-object v2

    .line 81
    const/16 v1, 0xb

    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->hora(Landroid/content/Context;)I

    move-result v3

    invoke-virtual {v2, v1, v3}, Ljava/util/Calendar;->set(II)V

    .line 82
    const/16 v1, 0xc

    invoke-virtual {v2, v1, v7}, Ljava/util/Calendar;->set(II)V

    .line 83
    const/16 v1, 0xd

    invoke-virtual {v2, v1, v4}, Ljava/util/Calendar;->set(II)V

    .line 84
    invoke-virtual {v2}, Ljava/util/Calendar;->getTimeInMillis()J

    move-result-wide v4

    invoke-static {}, Ljava/lang/System;->currentTimeMillis()J

    move-result-wide v8

    cmp-long v1, v4, v8

    if-gtz v1, :cond_2

    const/4 v1, 0x1

    invoke-virtual {v2, v7, v1}, Ljava/util/Calendar;->add(II)V

    .line 85
    :cond_2
    const/4 v1, 0x0

    :try_start_0
    invoke-virtual {v2}, Ljava/util/Calendar;->getTimeInMillis()J

    move-result-wide v2

    const-wide/32 v4, 0x5265c00

    invoke-virtual/range {v0 .. v6}, Landroid/app/AlarmManager;->setInexactRepeating(IJJLandroid/app/PendingIntent;)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto :goto_0

    :catch_0
    move-exception v0

    goto :goto_0
.end method

.method public static revisar(Landroid/content/Context;)V
    .locals 12

    .prologue
    const/4 v1, 0x0

    .line 99
    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->sp(Landroid/content/Context;)Landroid/content/SharedPreferences;

    move-result-object v3

    .line 100
    const-string v0, "activo"

    invoke-interface {v3, v0, v1}, Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z

    move-result v0

    if-eqz v0, :cond_0

    invoke-static {p0}, Lhn/hato/ganadero/Avisos;->permiso(Landroid/content/Context;)Z

    move-result v0

    if-nez v0, :cond_1

    .line 127
    :cond_0
    :goto_0
    return-void

    .line 101
    :cond_1
    invoke-static {}, Lhn/hato/ganadero/Avisos;->hoy()Ljava/lang/String;

    move-result-object v4

    .line 102
    const-string v0, "ultimo"

    const-string v2, ""

    invoke-interface {v3, v0, v2}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v4, v0}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v0

    if-nez v0, :cond_0

    .line 104
    :try_start_0
    new-instance v5, Lorg/json/JSONArray;

    const-string v0, "cola"

    const-string v2, "[]"

    invoke-interface {v3, v0, v2}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-direct {v5, v0}, Lorg/json/JSONArray;-><init>(Ljava/lang/String;)V

    .line 105
    new-instance v6, Lorg/json/JSONObject;

    const-string v0, "enviados"

    const-string v2, "{}"

    invoke-interface {v3, v0, v2}, Landroid/content/SharedPreferences;->getString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-direct {v6, v0}, Lorg/json/JSONObject;-><init>(Ljava/lang/String;)V

    .line 106
    const/4 v0, 0x0

    move v2, v1

    .line 107
    :goto_1
    invoke-virtual {v5}, Lorg/json/JSONArray;->length()I

    move-result v1

    if-ge v2, v1, :cond_6

    .line 108
    invoke-virtual {v5, v2}, Lorg/json/JSONArray;->getJSONObject(I)Lorg/json/JSONObject;

    move-result-object v1

    .line 109
    const-string v7, "f"

    invoke-virtual {v1, v7, v4}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v7

    .line 110
    invoke-virtual {v7, v4}, Ljava/lang/String;->compareTo(Ljava/lang/String;)I

    move-result v7

    if-lez v7, :cond_3

    .line 107
    :cond_2
    :goto_2
    add-int/lit8 v1, v2, 0x1

    move v2, v1

    goto :goto_1

    .line 111
    :cond_3
    const-string v7, "hasta"

    const-string v8, ""

    invoke-virtual {v1, v7, v8}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v7

    .line 112
    invoke-virtual {v7}, Ljava/lang/String;->length()I

    move-result v8

    if-lez v8, :cond_4

    invoke-virtual {v7, v4}, Ljava/lang/String;->compareTo(Ljava/lang/String;)I

    move-result v7

    if-ltz v7, :cond_2

    .line 113
    :cond_4
    const-string v7, "id"

    invoke-virtual {v1, v7}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v7

    invoke-virtual {v6, v7}, Lorg/json/JSONObject;->has(Ljava/lang/String;)Z

    move-result v7

    if-nez v7, :cond_2

    .line 114
    if-eqz v0, :cond_5

    const-string v7, "p"

    const-wide/16 v8, 0x0

    invoke-virtual {v1, v7, v8, v9}, Lorg/json/JSONObject;->optDouble(Ljava/lang/String;D)D

    move-result-wide v8

    const-string v7, "p"

    const-wide/16 v10, 0x0

    invoke-virtual {v0, v7, v10, v11}, Lorg/json/JSONObject;->optDouble(Ljava/lang/String;D)D

    move-result-wide v10

    cmpl-double v7, v8, v10

    if-lez v7, :cond_2

    :cond_5
    move-object v0, v1

    goto :goto_2

    .line 116
    :cond_6
    if-eqz v0, :cond_0

    .line 117
    const-string v1, "t"

    invoke-virtual {v0, v1}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    const-string v2, "x"

    invoke-virtual {v0, v2}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v2

    const-string v5, "ir"

    const-string v7, "#hoy"

    invoke-virtual {v0, v5, v7}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    move-result-object v5

    invoke-static {p0, v1, v2, v5}, Lhn/hato/ganadero/Avisos;->mostrar(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V

    .line 118
    const-string v1, "id"

    invoke-virtual {v0, v1}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v0

    invoke-virtual {v6, v0, v4}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    .line 120
    new-instance v1, Lorg/json/JSONObject;

    invoke-direct {v1}, Lorg/json/JSONObject;-><init>()V

    .line 121
    invoke-virtual {v6}, Lorg/json/JSONObject;->keys()Ljava/util/Iterator;

    move-result-object v2

    .line 122
    invoke-static {}, Ljava/util/Calendar;->getInstance()Ljava/util/Calendar;

    move-result-object v0

    const/4 v5, 0x5

    const/16 v7, -0x3c

    invoke-virtual {v0, v5, v7}, Ljava/util/Calendar;->add(II)V

    .line 123
    new-instance v5, Ljava/text/SimpleDateFormat;

    const-string v7, "yyyy-MM-dd"

    sget-object v8, Ljava/util/Locale;->US:Ljava/util/Locale;

    invoke-direct {v5, v7, v8}, Ljava/text/SimpleDateFormat;-><init>(Ljava/lang/String;Ljava/util/Locale;)V

    invoke-virtual {v0}, Ljava/util/Calendar;->getTime()Ljava/util/Date;

    move-result-object v0

    invoke-virtual {v5, v0}, Ljava/text/SimpleDateFormat;->format(Ljava/util/Date;)Ljava/lang/String;

    move-result-object v5

    .line 124
    :cond_7
    :goto_3
    invoke-interface {v2}, Ljava/util/Iterator;->hasNext()Z

    move-result v0

    if-eqz v0, :cond_8

    invoke-interface {v2}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    move-result-object v0

    check-cast v0, Ljava/lang/String;

    invoke-virtual {v6, v0}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v7

    invoke-virtual {v7, v5}, Ljava/lang/String;->compareTo(Ljava/lang/String;)I

    move-result v8

    if-ltz v8, :cond_7

    invoke-virtual {v1, v0, v7}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    goto :goto_3

    .line 126
    :catch_0
    move-exception v0

    goto/16 :goto_0

    .line 125
    :cond_8
    invoke-interface {v3}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v2, "enviados"

    invoke-virtual {v1}, Lorg/json/JSONObject;->toString()Ljava/lang/String;

    move-result-object v1

    invoke-interface {v0, v2, v1}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v1, "ultimo"

    invoke-interface {v0, v1, v4}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    goto/16 :goto_0
.end method

.method private static sp(Landroid/content/Context;)Landroid/content/SharedPreferences;
    .locals 2

    .prologue
    .line 34
    const-string v0, "rumi_avisos"

    const/4 v1, 0x0

    invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;

    move-result-object v0

    return-object v0
.end method

.method public static tomarIr()Ljava/lang/String;
    .locals 2

    .prologue
    .line 171
    sget-object v0, Lhn/hato/ganadero/Avisos;->ir:Ljava/lang/String;

    const/4 v1, 0x0

    sput-object v1, Lhn/hato/ganadero/Avisos;->ir:Ljava/lang/String;

    if-nez v0, :cond_0

    const-string v0, ""

    :cond_0
    return-object v0
.end method

.method public static widget(Landroid/content/Context;Ljava/lang/String;)V
    .locals 3

    .prologue
    .line 151
    const-string v0, "rumi_widget"

    const/4 v1, 0x0

    invoke-virtual {p0, v0, v1}, Landroid/content/Context;->getSharedPreferences(Ljava/lang/String;I)Landroid/content/SharedPreferences;

    move-result-object v0

    invoke-interface {v0}, Landroid/content/SharedPreferences;->edit()Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    const-string v1, "datos"

    if-nez p1, :cond_0

    const-string p1, "{}"

    :cond_0
    invoke-interface {v0, v1, p1}, Landroid/content/SharedPreferences$Editor;->putString(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;

    move-result-object v0

    invoke-interface {v0}, Landroid/content/SharedPreferences$Editor;->apply()V

    .line 153
    :try_start_0
    invoke-static {p0}, Landroid/appwidget/AppWidgetManager;->getInstance(Landroid/content/Context;)Landroid/appwidget/AppWidgetManager;

    move-result-object v0

    .line 154
    new-instance v1, Landroid/content/ComponentName;

    const-class v2, Lhn/hato/ganadero/RumiWidget;

    invoke-direct {v1, p0, v2}, Landroid/content/ComponentName;-><init>(Landroid/content/Context;Ljava/lang/Class;)V

    invoke-virtual {v0, v1}, Landroid/appwidget/AppWidgetManager;->getAppWidgetIds(Landroid/content/ComponentName;)[I

    move-result-object v1

    .line 155
    if-eqz v1, :cond_1

    array-length v2, v1

    if-lez v2, :cond_1

    invoke-static {p0, v0, v1}, Lhn/hato/ganadero/RumiWidget;->pintar(Landroid/content/Context;Landroid/appwidget/AppWidgetManager;[I)V
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    .line 157
    :cond_1
    :goto_0
    return-void

    .line 156
    :catch_0
    move-exception v0

    goto :goto_0
.end method
