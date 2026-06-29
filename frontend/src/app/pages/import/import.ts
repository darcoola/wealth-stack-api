import { Component, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { FileUpload, FileUploadModule } from 'primeng/fileupload';
import { SelectModule } from 'primeng/select';
import { ImportResult } from '../../core/import-result';
import { ImportService } from '../../core/import.service';

@Component({
  selector: 'app-import',
  imports: [FormsModule, CardModule, SelectModule, FileUploadModule, ButtonModule],
  templateUrl: './import.html',
  styleUrl: './import.scss',
})
export class Import {
  private readonly service = inject(ImportService);
  private readonly router = inject(Router);

  private readonly fileUpload = viewChild<FileUpload>('fileUpload');

  protected readonly banks = this.service.banks;
  protected readonly selectedBank = signal<string | null>(null);

  protected readonly uploading = signal(false);
  protected readonly result = signal<ImportResult | null>(null);
  protected readonly error = signal<string | null>(null);

  /** Server multipart limit is 10MB. */
  protected readonly maxFileSize = 10 * 1024 * 1024;

  protected onUpload(event: { files: File[] }): void {
    const bankName = this.selectedBank();
    const file = event.files[0];
    if (!bankName || !file) {
      return;
    }

    this.uploading.set(true);
    this.result.set(null);
    this.error.set(null);

    this.service.importStatement(bankName, file).subscribe({
      next: (result) => {
        this.result.set(result);
        this.uploading.set(false);
        this.fileUpload()?.clear();
      },
      error: (err) => {
        this.error.set(err?.error?.error ?? 'Import failed. Check the file and selected bank.');
        this.uploading.set(false);
      },
    });
  }

  protected goToOperations(): void {
    this.router.navigate(['/operations']);
  }
}
