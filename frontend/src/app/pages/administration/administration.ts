import { Component, DOCUMENT, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { FileUpload, FileUploadModule } from 'primeng/fileupload';
import { SelectModule } from 'primeng/select';
import { DataBackupService } from '../../core/data-backup.service';
import { OperationsService } from '../../core/operations.service';

type ImportMode = 'merge' | 'replace';

@Component({
  selector: 'app-administration',
  imports: [FormsModule, ButtonModule, FileUploadModule, SelectModule],
  templateUrl: './administration.html',
  styleUrl: './administration.scss',
})
export class Administration {
  private readonly document = inject(DOCUMENT);
  private readonly operations = inject(OperationsService);
  private readonly backup = inject(DataBackupService);

  private readonly backupUpload = viewChild<FileUpload>('backupUpload');

  protected readonly deleting = signal(false);
  protected readonly clearing = signal(false);
  protected readonly exporting = signal(false);
  protected readonly importing = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<string | null>(null);

  protected readonly importModes: { id: ImportMode; label: string }[] = [
    { id: 'merge', label: 'Merge into existing data' },
    { id: 'replace', label: 'Replace all existing data' },
  ];
  protected readonly importMode = signal<ImportMode>('merge');

  protected exportData(): void {
    this.startAction(this.exporting);
    this.backup.export().subscribe({
      next: ({ blob, fileName }) => {
        const url = URL.createObjectURL(blob);
        const link = this.document.createElement('a');
        link.href = url;
        link.download = fileName;
        link.click();
        // Revoking right after click() can cancel the download in some browsers.
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        this.result.set(`Exported your data to ${fileName}.`);
        this.exporting.set(false);
      },
      error: (err) => {
        this.error.set(this.message(err, 'Could not export data. Is the backend running?'));
        this.exporting.set(false);
      },
    });
  }

  protected async importData(event: { files: File[] }): Promise<void> {
    const file = event.files[0];
    if (!file) {
      return;
    }
    const replace = this.importMode() === 'replace';
    if (
      replace &&
      !confirm(
        'Replace ALL existing data with this backup? Every current operation, category, group and account mapping is deleted first. This cannot be undone.'
      )
    ) {
      this.backupUpload()?.clear();
      return;
    }

    this.startAction(this.importing);
    const content = await file.text();
    this.backup.import(content, replace).subscribe({
      next: (r) => {
        this.result.set(
          `${replace ? 'Replaced your data with' : 'Merged'} ${file.name}: ${r.operationsImported} operations added, ` +
            `${r.operationsOverwritten} updated; ${r.categoriesCreated} categories, ${r.categoryGroupsCreated} groups ` +
            `and ${r.accountMappingsCreated} account mappings created.`
        );
        this.importing.set(false);
        this.backupUpload()?.clear();
      },
      error: (err) => {
        this.error.set(this.message(err, 'Import failed. Is this a WealthStack backup file?'));
        this.importing.set(false);
        this.backupUpload()?.clear();
      },
    });
  }

  protected removeAllOperations(): void {
    if (!confirm('Delete ALL operations? This permanently removes every imported operation and cannot be undone. Account mappings, categories and groups are kept.')) {
      return;
    }
    this.startAction(this.deleting);
    this.operations.deleteAll().subscribe({
      next: ({ deletedCount }) => {
        this.result.set(`Deleted ${deletedCount} operation${deletedCount === 1 ? '' : 's'}.`);
        this.deleting.set(false);
      },
      error: (err) => {
        this.error.set(this.message(err, 'Could not delete operations. Is the backend running?'));
        this.deleting.set(false);
      },
    });
  }

  protected clearAllData(): void {
    if (!confirm('Delete ALL data? Every operation, category, group and account mapping is permanently removed. This cannot be undone.')) {
      return;
    }
    this.startAction(this.clearing);
    this.backup.clearAll().subscribe({
      next: (r) => {
        this.result.set(
          `Deleted ${r.operationsDeleted} operations, ${r.categoriesDeleted} categories, ${r.categoryGroupsDeleted} groups ` +
            `and ${r.accountMappingsDeleted} account mappings.`
        );
        this.clearing.set(false);
      },
      error: (err) => {
        this.error.set(this.message(err, 'Could not delete data. Is the backend running?'));
        this.clearing.set(false);
      },
    });
  }

  private startAction(busy: { set(value: boolean): void }): void {
    busy.set(true);
    this.error.set(null);
    this.result.set(null);
  }

  private message(err: unknown, fallback: string): string {
    const detail = (err as { error?: { error?: string } })?.error?.error;
    return detail ?? fallback;
  }
}
