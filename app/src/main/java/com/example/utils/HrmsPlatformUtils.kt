package com.example.utils

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.FileProvider
import com.example.data.PayrollRecord
import com.example.data.ServiceItem
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

object HrmsPlatformUtils {

    fun formatCurrency(amount: Double): String =
        NumberFormat.getCurrencyInstance(Locale.US).format(amount)

    fun createSelfieTempUri(context: Context): Uri {
        val file = File(context.cacheDir, "selfie_checkin_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    @SuppressLint("MissingPermission")
    fun fetchLastCoarseLocation(context: Context): Location? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            providers.firstNotNullOfOrNull { provider ->
                try {
                    if (lm.isProviderEnabled(provider)) lm.getLastKnownLocation(provider) else null
                } catch (_: SecurityException) {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun generateAndSharePayslipPdf(
        context: Context,
        payroll: PayrollRecord,
        companyName: String
    ): Result<Unit> {
        return try {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = doc.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val headerPaint = Paint().apply {
                color = Color.parseColor("#0F4C5C")
                style = Paint.Style.FILL
            }
            canvas.drawRect(0f, 0f, 595f, 110f, headerPaint)

            val titlePaint = Paint().apply {
                color = Color.WHITE
                textSize = 24f
                isFakeBoldText = true
            }
            val subPaint = Paint().apply {
                color = Color.parseColor("#D2ECEF")
                textSize = 13f
            }
            canvas.drawText(companyName.ifBlank { "KrewHR" }, 40f, 52f, titlePaint)
            canvas.drawText("Official Employee Payslip • ${payroll.periodLabel}", 40f, 78f, subPaint)

            val bodyTitle = Paint().apply {
                color = Color.parseColor("#0F172A")
                textSize = 16f
                isFakeBoldText = true
            }
            val bodyText = Paint().apply {
                color = Color.parseColor("#334155")
                textSize = 13f
            }
            val linePaint = Paint().apply {
                color = Color.parseColor("#CBD5E1")
                strokeWidth = 1f
            }

            var y = 160f
            canvas.drawText("Employee Summary", 40f, y, bodyTitle)
            y += 28f
            canvas.drawText("Name: ${payroll.employeeName} (${payroll.employeeCode})", 40f, y, bodyText)
            y += 22f
            canvas.drawText("Department: ${payroll.department}", 40f, y, bodyText)
            y += 22f
            canvas.drawText("Days Worked: ${payroll.daysWorked}   •   Status: ${payroll.status}", 40f, y, bodyText)
            y += 24f
            canvas.drawLine(40f, y, 555f, y, linePaint)

            y += 36f
            canvas.drawText("Earnings & Statutory Deductions", 40f, y, bodyTitle)
            y += 30f
            val rows = listOf(
                "Base Monthly Salary" to formatCurrency(payroll.basePay),
                "Overtime & Shift Allowance" to formatCurrency(payroll.overtimePay),
                "Federal & State Tax Withholding" to "-${formatCurrency(payroll.taxDeduction)}",
                "Health & 401k Benefits" to "-${formatCurrency(payroll.benefitsDeduction)}"
            )
            rows.forEach { (label, value) ->
                canvas.drawText(label, 40f, y, bodyText)
                canvas.drawText(value, 430f, y, bodyText)
                y += 26f
            }

            y += 10f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 34f
            val netPaint = Paint().apply {
                color = Color.parseColor("#0F4C5C")
                textSize = 18f
                isFakeBoldText = true
            }
            canvas.drawText("Net Take-Home Pay", 40f, y, netPaint)
            canvas.drawText(formatCurrency(payroll.netPay), 430f, y, netPaint)

            doc.finishPage(page)
            val safeFile = "payslip_${payroll.employeeCode}_${payroll.periodLabel.replace(" ", "_")}.pdf"
            val outFile = File(context.cacheDir, safeFile)
            FileOutputStream(outFile).use { doc.writeTo(it) }
            doc.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Payslip - ${payroll.employeeName} (${payroll.periodLabel})")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Download or Share Payslip PDF"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildAccountingCsv(payroll: List<PayrollRecord>, expenses: List<ServiceItem>): String {
        val sb = StringBuilder()
        sb.appendLine("Type,Code,Employee,Department,PeriodOrCategory,GrossOrAmount,TaxDeduction,BenefitsDeduction,NetPay,Status")
        payroll.forEach { p ->
            sb.appendLine("PAYROLL,${p.employeeCode},\"${p.employeeName}\",${p.department},${p.periodLabel},${p.basePay + p.overtimePay},${p.taxDeduction},${p.benefitsDeduction},${p.netPay},${p.status}")
        }
        expenses.filter { it.kind == "EXPENSE" }.forEach { e ->
            sb.appendLine("EXPENSE,EXP-${e.id.take(5)},\"${e.requesterName}\",${e.category},\"${e.title}\",${e.amount},0,0,${e.amount},${e.status}")
        }
        return sb.toString()
    }

    fun openCalendarShiftSync(context: Context, title: String, description: String) {
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
        }
        val end = Calendar.getInstance().apply {
            timeInMillis = start.timeInMillis
            set(Calendar.HOUR_OF_DAY, 18)
        }
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.timeInMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.timeInMillis)
        }
        context.startActivity(intent)
    }

    fun createDeviceMfaIntent(context: Context): Intent? {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return null
        return if (km.isDeviceSecure) {
            @Suppress("DEPRECATION")
            km.createConfirmDeviceCredentialIntent(
                "KrewHR MFA Verification",
                "Confirm your device PIN, pattern, or biometric to unlock sensitive HR & payroll actions"
            )
        } else null
    }
}
