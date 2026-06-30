import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
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
    IconFieldModule,
    InputIconModule,
    ButtonModule,
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

  /** Net of all loaded operations (credits minus debits). */
  protected readonly total = computed(() =>
    this.operations().reduce((sum, op) => sum + op.amount, 0),
  );

  constructor() {
    this.load();
    this.loadCategories();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.getAll().subscribe({
      next: (operations) => {
        this.operations.set(operations);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load operations. Is the backend running?');
        this.loading.set(false);
      },
    });
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
            o.id === op.id ? { ...o, categoryId: updated.categoryId, category: updated.category } : o,
          ),
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
            return u ? { ...o, categoryId: u.categoryId, category: u.category } : o;
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
}
