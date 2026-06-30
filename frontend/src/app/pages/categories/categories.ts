import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { Category } from '../../core/category';
import { CategoriesService } from '../../core/categories.service';

@Component({
  selector: 'app-categories',
  imports: [FormsModule, TableModule, InputTextModule, ButtonModule],
  templateUrl: './categories.html',
  styleUrl: './categories.scss',
})
export class Categories {
  private readonly service = inject(CategoriesService);

  protected readonly categories = signal<Category[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly newName = signal('');

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
    this.service.create(name).subscribe({
      next: () => {
        this.newName.set('');
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
    this.error.set(null);
    this.service.rename(category.id, trimmed).subscribe({
      next: () => this.load(),
      error: (err) => {
        this.error.set(this.message(err, `Could not rename "${category.name}".`));
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
