import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, Download, FileSpreadsheet, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { adminApi, downloadBlob } from '@/api/admin'
import { contentApi, contentKeys } from '@/api/content'
import { usePermissions } from '@/api/portal'
import { NativeSelect } from '@/components/ui/native-select'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { errorMessage } from '@/lib/errors'
import type { ImportReport } from '@/types/admin'

/**
 * Bulk import from .xlsx/.csv. "Validate" does a dry run (the server checks every row and
 * rolls back). "Import" saves all rows, or none if any row is invalid.
 */
export function ImportDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (o: boolean) => void }) {
  const qc = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [autoCreate, setAutoCreate] = useState(false)
  const [after, setAfter] = useState<'DRAFT' | 'SUBMIT' | 'PUBLISH'>('DRAFT')
  const { can } = usePermissions()
  const settings = useQuery({ queryKey: contentKeys.settings, queryFn: contentApi.settings, enabled: open })
  const canPublishDirectly = can('question.publish') && settings.data?.reviewRequired === false
  const [report, setReport] = useState<ImportReport | null>(null)

  const run = useMutation({
    mutationFn: (dryRun: boolean) => adminApi.importQuestions(file!, dryRun, autoCreate, after),
    onSuccess: (r) => {
      setReport(r)
      if (!r.dryRun && r.importedRows > 0) {
        toast.success(`Imported ${r.importedRows} questions`)
        void qc.invalidateQueries({ queryKey: ['admin', 'questions'] })
      }
    },
    onError: (e) => toast.error(errorMessage(e)),
  })
  const template = useMutation({
    mutationFn: adminApi.importTemplate,
    onSuccess: (blob) => downloadBlob(blob, 'question-import-template.csv'),
    onError: (e) => toast.error(errorMessage(e)),
  })

  const close = (o: boolean) => {
    if (run.isPending) return
    if (!o) { setFile(null); setReport(null); setAutoCreate(false) }
    onOpenChange(o)
  }

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent className="sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Import questions</DialogTitle>
          <DialogDescription>
            Upload an Excel (.xlsx) or CSV file. If any row has an error, nothing is saved, so you can fix the sheet and upload it again.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          <Button variant="link" className="h-auto p-0" loading={template.isPending} onClick={() => template.mutate()}>
            <Download /> Download the template (with example rows)
          </Button>
          <label className="hover:bg-muted/50 flex cursor-pointer flex-col items-center gap-2 rounded-lg border border-dashed p-6 text-center text-sm">
            <FileSpreadsheet className="text-muted-foreground size-8" />
            {file ? <span className="font-medium">{file.name}</span> : <span>Choose a .xlsx or .csv file</span>}
            <input type="file" accept=".xlsx,.csv" className="sr-only"
                   onChange={(e) => { setFile(e.target.files?.[0] ?? null); setReport(null) }} />
          </label>
          <label className="flex items-start gap-2 text-sm">
            <Checkbox checked={autoCreate} onCheckedChange={setAutoCreate} className="mt-0.5" />
            Create missing chapters and topics automatically (exams and subjects must already exist)
          </label>
          <div className="flex flex-wrap items-center gap-2 text-sm">
            <label htmlFor="import-after" className="font-medium">After import</label>
            <NativeSelect id="import-after" className="h-8 w-auto" value={after}
                          onChange={(e) => setAfter(e.target.value as 'DRAFT' | 'SUBMIT' | 'PUBLISH')}>
              <option value="DRAFT">Keep as drafts</option>
              <option value="SUBMIT">Submit for review (unassigned queue)</option>
              {canPublishDirectly && <option value="PUBLISH">Publish right away</option>}
            </NativeSelect>
          </div>
          <p className="text-muted-foreground text-xs">Optional Hindi columns: questionTextHi, optionAHi…optionDHi, solutionTextHi. New metadata: subTopic, expectedTimeSec, cognitiveLevel, sourceType, shift, concepts.</p>

          {report && (
            <div className="rounded-lg border p-3 text-sm" role="status">
              {report.errors.length === 0 ? (
                <p className="text-success flex items-center gap-2 font-medium">
                  <CheckCircle2 className="size-4" />
                  {report.dryRun ? `All ${report.validRows} rows are valid. Ready to import.` : `Imported ${report.importedRows} of ${report.totalRows} rows.`}
                </p>
              ) : (
                <>
                  <p className="text-destructive flex items-center gap-2 font-medium">
                    <XCircle className="size-4" /> {report.errors.length} problem(s) in {report.totalRows} rows. Nothing was saved.
                  </p>
                  <ul className="mt-2 max-h-48 space-y-1 overflow-y-auto">
                    {report.errors.map((e, i) => (
                      <li key={i}><span className="font-medium">Row {e.row}:</span> {e.message}</li>
                    ))}
                  </ul>
                </>
              )}
            </div>
          )}
        </div>

        <DialogFooter>
          <Button variant="outline" disabled={!file || run.isPending} loading={run.isPending && run.variables === true}
                  onClick={() => run.mutate(true)}>Validate only</Button>
          <Button disabled={!file || run.isPending} loading={run.isPending && run.variables === false}
                  onClick={() => run.mutate(false)}>Import</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
