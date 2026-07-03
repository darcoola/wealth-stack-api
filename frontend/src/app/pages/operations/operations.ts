import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { SelectButtonModule } from 'primeng/selectbutton';
import { Category } from '../../core/category';
import { CategoriesService } from '../../core/categories.service';
import { Operation } from '../../core/operation';
import { OperationsService } from '../../core/operations.service';

@Component({
  selector: 'app-operations',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    TableModule,
    TagModule,
    SelectModule,
    InputTextModule,
    DatePickerModule,
    IconFieldModule,
    InputIconModule,
    ButtonModule,
    ToggleSwitchModule,
    SelectButtonModule,
  ],
  templateUrl: './operations.html',
  styleUrl: './operations.scss',
})
export class Operations {
  private readonly service = inject(OperationsService);
  private readonly categoriesService = inject(CategoriesService);

  protected readonly operations = signal<Operation[]>([]);
  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Rows checked for a bulk action, and the category chosen in the bulk toolbar. */
  protected readonly selected = signal<Operation[]>([]);
  protected readonly bulkCategoryId = signal<number | null>(null);

  protected readonly quickFilterOptions = signal([
    { label: 'All', value: 'all' },
    { label: 'Needs verification', value: 'verify' },
    { label: 'Uncategorized', value: 'uncategorized' },
  ]);
  protected readonly quickFilter = signal<'all' | 'verify' | 'uncategorized'>('all');

  protected readonly showNeedsVerificationOnly = computed(() => this.quickFilter() === 'verify');
  protected readonly showUncategorizedOnly = computed(() => this.quickFilter() === 'uncategorized');
  protected readonly selectedMonthDate = signal<Date | null>(null);
  protected readonly totalRecords = signal(0);
  protected lastTableEvent: any = null;

  /** Net of all loaded operations (credits minus debits). */
  protected readonly total = computed(() =>
    this.operations().reduce((sum, op) => sum + op.amount, 0),
  );

  constructor() {
    this.loadCategories();
  }

  protected load(event?: any): void {
    if (event) {
      this.lastTableEvent = event;
    } else {
      event = this.lastTableEvent;
    }

    this.loading.set(true);
    this.error.set(null);

    const params: any = {};
    if (event) {
      params.page = Math.floor((event.first ?? 0) / (event.rows ?? 20));
      params.size = event.rows ?? 20;
      if (event.globalFilter) {
        params.globalFilter = event.globalFilter;
      }
      if (event.sortField) {
        params.sort = `${event.sortField},${event.sortOrder === 1 ? 'asc' : 'desc'}`;
      }
    } else {
      params.page = 0;
      params.size = 20;
    }

    params.needsVerificationOnly = this.showNeedsVerificationOnly();
    params.uncategorizedOnly = this.showUncategorizedOnly();

    const monthDate = this.selectedMonthDate();
    if (monthDate) {
      const y = monthDate.getFullYear();
      const m = String(monthDate.getMonth() + 1).padStart(2, '0');
      params.monthDate = `${y}-${m}`;
    }

    this.service.getAll(params).subscribe({
      next: (page) => {
        this.operations.set(page.content);
        this.totalRecords.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load operations. Is the backend running?');
        this.loading.set(false);
      },
    });
  }

  protected onFilterChange(): void {
    if (this.lastTableEvent) {
      this.lastTableEvent.first = 0;
    }
    this.load();
  }

  protected loadCategories(): void {
    this.categoriesService.getAll().subscribe({
      next: (categories) => this.categories.set(categories),
    });
  }

  /** Persist a category (re)assignment and reflect it on the row in place. */
  protected assign(op: Operation, categoryId: number | null): void {
    this.service.assignCategory(op.id, categoryId).subscribe({
      next: (updated) => {
        this.operations.update((ops) =>
          ops.map((o) =>
            o.id === op.id ? { ...o, categoryId: updated.categoryId, category: updated.category, needsVerification: updated.needsVerification } : o,
          ),
        );
      },
    });
  }

  /**
   * Persist an edit to the free-text note and reflect it on the row. Skips the request when the
   * value is unchanged (e.g. the user opened the editor and tabbed out without typing). A blank
   * value clears the note. Works for any row, including bank-imported ones that started without one.
   */
  protected updateInfo(op: Operation, value: string): void {
    const next = value.trim() === '' ? null : value.trim();
    if (next === op.additionalInfo) return;
    this.service.updateAdditionalInfo(op.id, next).subscribe({
      next: (updated) => {
        this.operations.update((ops) =>
          ops.map((o) => (o.id === op.id ? { ...o, additionalInfo: updated.additionalInfo } : o)),
        );
      },
    });
  }

  /** Assign the toolbar category to every selected row, then clear the selection. */
  protected assignSelected(): void {
    const ids = this.selected().map((o) => o.id);
    if (ids.length === 0) return;
    this.service.assignCategoryBulk(ids, this.bulkCategoryId()).subscribe({
      next: (updated) => {
        const byId = new Map(updated.map((u) => [u.id, u]));
        this.operations.update((ops) =>
          ops.map((o) => {
            const u = byId.get(o.id);
            return u ? { ...o, categoryId: u.categoryId, category: u.category, needsVerification: u.needsVerification } : o;
          }),
        );
        this.selected.set([]);
      },
    });
  }

  /** Delete every selected row (after confirmation), then drop them from the table. */
  protected deleteSelected(): void {
    const ids = this.selected().map((o) => o.id);
    if (ids.length === 0) return;
    if (!confirm(`Delete ${ids.length} operation(s)? This cannot be undone.`)) return;
    this.service.deleteBulk(ids).subscribe({
      next: () => {
        const removed = new Set(ids);
        this.operations.update((ops) => ops.filter((o) => !removed.has(o.id)));
        this.selected.set([]);
      },
    });
  }

  protected acceptPrediction(op: Operation): void {
    this.service.acceptPrediction(op.id).subscribe({
      next: (updated) => {
        this.operations.update((ops) =>
          ops.map((o) =>
            o.id === op.id ? { ...o, needsVerification: false } : o,
          ),
        );
      },
    });
  }

  protected acceptPredictionsSelected(): void {
    const ids = this.selected().map((o) => o.id);
    if (ids.length === 0) return;
    this.service.acceptPredictionsBulk(ids).subscribe({
      next: () => {
        const idSet = new Set(ids);
        this.operations.update((ops) =>
          ops.map((o) => (idSet.has(o.id) ? { ...o, needsVerification: false } : o)),
        );
        this.selected.set([]);
      },
    });
  }
}
