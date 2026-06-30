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
}
