import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { Category, CategoryType } from '../../core/category';
import { CategoriesService } from '../../core/categories.service';

@Component({
  selector: 'app-categories',
  imports: [FormsModule, TableModule, InputTextModule, SelectModule, ButtonModule],
  templateUrl: './categories.html',
  styleUrl: './categories.scss',
})
export class Categories {
  private readonly service = inject(CategoriesService);

  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly newName = signal('');
  protected readonly newType = signal<CategoryType>('SPENDING');

  protected readonly typeOptions: { label: string; value: CategoryType }[] = [
    { label: 'Spending', value: 'SPENDING' },
    { label: 'Income', value: 'INCOME' },
    { label: 'Others', value: 'OTHERS' },
  ];

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.getAll().subscribe({
      next: (categories) => {
        this.categories.set(categories);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load categories. Is the backend running?');
        this.loading.set(false);
      },
    });
  }

  protected add(): void {
    const name = this.newName().trim();
    if (!name) {
      return;
    }
    this.error.set(null);
    this.service.create(name, this.newType()).subscribe({
      next: () => {
        this.newName.set('');
        this.newType.set('SPENDING');
        this.load();
      },
      error: (err) => this.error.set(this.message(err, `Could not create "${name}".`)),
    });
  }

  protected rename(category: Category, name: string): void {
    const trimmed = name.trim();
    if (!trimmed || trimmed === category.name) {
      this.load();
      return;
    }
    this.update(category, trimmed, category.type);
  }

  protected changeType(category: Category, type: CategoryType): void {
    if (type === category.type) {
      return;
    }
    this.update(category, category.name, type);
  }

  private update(category: Category, name: string, type: CategoryType): void {
    this.error.set(null);
    this.service.update(category.id, name, type).subscribe({
      next: () => this.load(),
      error: (err) => {
        this.error.set(this.message(err, `Could not update "${category.name}".`));
        this.load();
      },
    });
  }

  protected remove(category: Category): void {
    if (!confirm(`Delete category "${category.name}"? Operations using it become Uncategorized.`)) {
      return;
    }
    this.error.set(null);
    this.service.delete(category.id).subscribe({
      next: () => this.load(),
      error: (err) => this.error.set(this.message(err, `Could not delete "${category.name}".`)),
    });
  }

  private message(err: unknown, fallback: string): string {
    const detail = (err as { error?: { error?: string } })?.error?.error;
    return detail ?? fallback;
  }
}
