package com.ptut.expense.di

import com.ptut.expense.data.local.PtutDatabaseContext
import com.ptut.expense.platform.ocr.SlipTextReader
import com.ptut.expense.platform.photos.SlipSource
import org.koin.core.module.Module

/**
 * A platform's real pair: where slip pictures come from, and what reads them.
 *
 * The two travel together because they are only ever bound together — a folder scanner with no
 * reader files nothing, and a reader with no folder is never handed anything. Binding one type
 * lets [slipSeamModule] ask one question, `is this platform wired up yet`, instead of two.
 */
data class SlipSeams(val source: SlipSource, val reader: SlipTextReader)

/**
 * The platform's own half of the slip graph. Android binds MediaStore, the share inbox and ML Kit;
 * iOS binds nothing until #18.
 *
 * A module rather than a plain factory, because the parts have callers of their own: the Activity
 * hands a shared picture to the same inbox instance the importer scans, and two instances would
 * leave the share sheet writing into a folder nothing reads. It takes the platform handle the graph
 * already carries, exactly as [ledgerFileStore] does.
 */
expect fun platformSlipModule(context: PtutDatabaseContext): Module
