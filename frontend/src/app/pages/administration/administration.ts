import { Component, inject, signal } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { OperationsService } from '../../core/operations.service';

@Component({
  selector: 'app-administration',
  imports: [ButtonModule],
  templateUrl: './administration.html',
  styleUrl: './administration.scss',
})
export class Administration {
  private readonly operations = inject(OperationsService);

  protected readonly deleting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<string | null>(null);

  protected removeAllOperations(): void {
    if (!confirm('Delete ALL operations? This permanently removes every imported operation and cannot be undone. Account mappings, categories and groups are kept.')) {
      return;
    }
    this.deleting.set(true);
    this.error.set(null);
    this.result.set(null);
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

  private message(err: unknown, fallback: string): string {
    const detail = (err as { error?: { error?: string } })?.error?.error;
    return detail ?? fallback;
  }
}
