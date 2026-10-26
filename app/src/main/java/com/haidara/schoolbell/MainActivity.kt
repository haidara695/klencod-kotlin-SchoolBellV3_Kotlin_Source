package com.haidara.schoolbell

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter

private val Navy = Color(0xFF142B63)
private val Gold = Color(0xFFF1C66B)
private val Background = Color(0xFFF0F4FA)

data class BellItem(val id: Long, val title: String, val subject: String, val grade: String, val section: String, val hour: Int, val minute: Int, val days: Set<Int>) {
    fun json() = JSONObject().put("id", id).put("title", title).put("subject", subject).put("grade", grade).put("section", section).put("hour", hour).put("minute", minute).put("days", JSONArray(days.sorted()))
    companion object { fun from(j: JSONObject): BellItem { val a = j.getJSONArray("days"); return BellItem(j.getLong("id"),j.getString("title"),j.optString("subject"),j.optString("grade"),j.optString("section"),j.getInt("hour"),j.getInt("minute"),(0 until a.length()).map { a.getInt(it) }.toSet()) } }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { SchoolBellScreen() } }
}

@Composable
fun SchoolBellScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("bell_v3", 0) }
    val events = remember { mutableStateListOf<BellItem>().apply { try { val arr=JSONArray(prefs.getString("events", "[]")); for (i in 0 until arr.length()) add(BellItem.from(arr.getJSONObject(i))) } catch (_: Exception) {} } }
    var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", true)) }
    var duration by remember { mutableIntStateOf(prefs.getInt("duration", 10)) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var editing by remember { mutableStateOf<BellItem?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val save = { prefs.edit().putString("events", JSONArray(events.map { it.json() }).toString()).putBoolean("enabled",enabled).putInt("duration",duration).apply() }
    LaunchedEffect(Unit) { while (true) { now=LocalDateTime.now(); delay(1000) } }
    val upcoming = events.flatMap { e -> (0..7).mapNotNull { offset -> val date=now.toLocalDate().plusDays(offset.toLong()); if (date.dayOfWeek.value in e.days) date.atTime(e.hour,e.minute).takeIf { it.isAfter(now) }?.let { it to e } else null } }.minByOrNull { it.first }
    val remaining = upcoming?.let { Duration.between(now,it.first).seconds.coerceAtLeast(0) } ?: 0L
    val countdown = "%02d:%02d:%02d".format(remaining/3600,remaining%3600/60,remaining%60)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri!=null) try { val root=JSONObject().put("format","school-bell-v3").put("version",1).put("enabled",enabled).put("duration",duration).put("events",JSONArray(events.map { it.json() })); context.contentResolver.openOutputStream(uri)?.use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }; message="تم تصدير النسخة" } catch(e:Exception) { message="فشل التصدير: ${e.message}" }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) try { val content=context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("ملف فارغ"); val root=JSONObject(content); require(root.getString("format")=="school-bell-v3"); val arr=root.getJSONArray("events"); require(arr.length()<=2000); val imported=(0 until arr.length()).map { BellItem.from(arr.getJSONObject(it)).also { b -> require(b.hour in 0..23 && b.minute in 0..59 && b.days.all { d -> d in 1..7 }) } }; events.clear(); events.addAll(imported); enabled=root.optBoolean("enabled",true); duration=root.optInt("duration",10).coerceIn(1,120); save(); message="تم استيراد ${events.size} جرس" } catch(e:Exception) { message="ملف غير صالح: ${e.message}" }
    }
    val share = { try { val root=JSONObject().put("format","school-bell-v3").put("version",1).put("enabled",enabled).put("duration",duration).put("events",JSONArray(events.map { it.json() })); val intent=Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT,root.toString(2)); context.startActivity(Intent.createChooser(intent,"مشاركة نسخة الجدول كنص JSON")) } catch(e:Exception) { message=e.message ?: "تعذر المشاركة" } }
    MaterialTheme(colorScheme=lightColorScheme(primary=Navy,secondary=Gold,background=Background)) {
        Column(Modifier.fillMaxSize().background(Background)) {
            LazyColumn(contentPadding=PaddingValues(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                item {
                    Card(colors=CardDefaults.cardColors(containerColor=Navy),shape=RoundedCornerShape(24.dp)) {
                        Column(Modifier.fillMaxWidth().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text("🔔 الجرس المدرسي الذكي V3 PRO",color=Color.White,fontWeight=FontWeight.Bold,fontSize=21.sp)
                            Text("الوقت المتبقي للجرس القادم",color=Color(0xFFC5D5F4))
                            Text(countdown,color=Gold,fontSize=38.sp,fontWeight=FontWeight.Bold)
                            Text(upcoming?.second?.let { "${it.title} • ${it.subject} • ${it.grade} ${it.section}" } ?: "لا يوجد جرس قادم",color=Color.White,textAlign=TextAlign.Center)
                            Text(upcoming?.first?.format(DateTimeFormatter.ofPattern("yyyy/MM/dd  HH:mm")) ?: "",color=Color(0xFFC5D5F4))
                        }
                    }
                }
                item { Card { Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) { Text("تفعيل الجدول",fontWeight=FontWeight.Bold); Switch(checked=enabled,onCheckedChange={enabled=it;save()}) } } }
                item { Card { Column(Modifier.padding(14.dp)) { Text("مدة الرنين: $duration ثانية"); Slider(value=duration.toFloat(),onValueChange={duration=it.toInt().coerceAtLeast(1)},onValueChangeFinished=save,valueRange=1f..120f) } } }
                item { Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { Button(onClick={editing=null;showEditor=true},modifier=Modifier.weight(1f)) { Text("+ جرس") }; OutlinedButton(onClick={export.launch("school-bell-backup.json")},modifier=Modifier.weight(1f)) { Text("تصدير") }; OutlinedButton(onClick={import.launch(arrayOf("application/json","text/plain"))},modifier=Modifier.weight(1f)) { Text("استيراد") } } }
                item { OutlinedButton(onClick=share,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Default.Share,null); Spacer(Modifier.width(8.dp)); Text("مشاركة نسخة الجدول") } }
                item { Text("جدول الأجراس (${events.size})",fontSize=20.sp,fontWeight=FontWeight.Bold) }
                items(events.sortedWith(compareBy({it.hour},{it.minute},{it.title})),key={it.id}) { event ->
                    Card(shape=RoundedCornerShape(16.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(event.title,fontWeight=FontWeight.Bold); Text("${event.subject} | ${event.grade} | شعبة ${event.section}",fontSize=12.sp); Text("%02d:%02d • أيام: %s".format(event.hour,event.minute,event.days.sorted().joinToString(",")),color=Navy,fontSize=12.sp) }
                        IconButton(onClick={editing=event;showEditor=true}) { Icon(Icons.Default.Edit,"تعديل") }
                        IconButton(onClick={events.remove(event);save()}) { Icon(Icons.Default.Delete,"حذف") }
                    } }
                }
                item { HorizontalDivider(); Text("تطوير أ. محمد حيدره",modifier=Modifier.fillMaxWidth(),textAlign=TextAlign.Center,fontWeight=FontWeight.Bold); TextButton(onClick={context.startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:785068859")))},modifier=Modifier.fillMaxWidth()) { Text("📞 785068859 — اضغط للاتصال") }; TextButton(onClick={try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/967785068859"))) } catch(e:Exception) { message="تعذر فتح واتساب" }},modifier=Modifier.fillMaxWidth()) { Text("التواصل عبر واتساب") } }
            }
        }
        if(message.isNotBlank()) AlertDialog(onDismissRequest={message=""},title={Text("إشعار")},text={Text(message)},confirmButton={TextButton(onClick={message=""}) { Text("حسنًا") }})
        if(showEditor) BellEditor(editing,onClose={showEditor=false},onSave={item -> events.removeAll { it.id==item.id };events.add(item);save();showEditor=false})
    }
}

@Composable
fun BellEditor(old: BellItem?,onClose:()->Unit,onSave:(BellItem)->Unit) {
    var title by remember(old) { mutableStateOf(old?.title ?: "الحصة الأولى") }
    var subject by remember(old) { mutableStateOf(old?.subject ?: "الحاسوب") }
    var grade by remember(old) { mutableStateOf(old?.grade ?: "الخامس") }
    var section by remember(old) { mutableStateOf(old?.section ?: "أ") }
    var hour by remember(old) { mutableStateOf((old?.hour ?: 7).toString()) }
    var minute by remember(old) { mutableStateOf((old?.minute ?: 30).toString()) }
    var days by remember(old) { mutableStateOf(old?.days ?: setOf(1,2,3,4,5)) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest=onClose,title={Text(if(old==null) "إضافة جرس" else "تعديل الجرس")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(title,{title=it},label={Text("اسم الحصة / الحدث")},singleLine=true)
            OutlinedTextField(subject,{subject=it},label={Text("المادة")},singleLine=true)
            OutlinedTextField(grade,{grade=it},label={Text("الصف")},singleLine=true)
            OutlinedTextField(section,{section=it},label={Text("الشعبة")},singleLine=true)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedTextField(hour,{hour=it},label={Text("الساعة 0–23")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f));OutlinedTextField(minute,{minute=it},label={Text("الدقيقة")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.weight(1f)) }
            Text("أيام التكرار: الإثنين إلى الأحد",fontSize=12.sp)
            for (group in listOf((1..4).toList(),(5..7).toList())) Row { group.forEach { day -> FilterChip(selected=day in days,onClick={days=if(day in days) days-day else days+day},label={Text(listOf("إث","ثل","أر","خم","جم","سب","أح")[day-1])},modifier=Modifier.padding(end=3.dp)) } }
            if(error.isNotEmpty()) Text(error,color=Color.Red)
        }
    },confirmButton={TextButton(onClick={ val h=hour.toIntOrNull();val m=minute.toIntOrNull(); if(title.isBlank() || h==null || h !in 0..23 || m==null || m !in 0..59 || days.isEmpty()) error="تحقق من الاسم والوقت والأيام" else onSave(BellItem(old?.id ?: System.nanoTime(),title.trim(),subject.trim(),grade.trim(),section.trim(),h,m,days)) }) { Text("حفظ") }},dismissButton={TextButton(onClick=onClose) { Text("إلغاء") }})
}