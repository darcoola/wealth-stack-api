import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { Category } from '../../core/category';
import { CategoryGroup } from '../../core/category-group';
import { CategoriesService } from '../../core/categories.service';
import { CategoryGroupsService } from '../../core/category-groups.service';

/** Sentinel select option for a category that belongs to no group. */
const NONE_GROUP = { label: '— None —', value: null as number | null };

@Component({
  selector: 'app-categories',
  imports: [FormsModule, TableModule, InputTextModule, SelectModule, ButtonModule],
  templateUrl: './categories.html',
  styleUrl: './categories.scss',
})
export class Categories {
  private readonly service = inject(CategoriesService);
  private readonly groupsService = inject(CategoryGroupsService);

  protected readonly categories = signal<Category[]>([]);
  protected readonly groups = signal<CategoryGroup[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly newName = signal('');
  protected readonly newGroupId = signal<number | null>(null);

  /** Group picker options: an "— None —" (Ungrouped) entry followed by every group. */
  protected readonly groupOptions = computed(() => [
    NONE_GROUP,
    ...this.groups().map((g) => ({ label: g.name, value: g.id as number | null })),
  ]);

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.groupsService.getAll().subscribe({
      next: (groups) => this.groups.set(groups),
      error: () => this.error.set('Could not load groups. Is the backend running?'),
    });
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
    this.service.create(name, this.newGroupId()).subscribe({
      next: () => {
        this.newName.set('');
        this.newGroupId.set(null);
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
    this.update(category, trimmed, category.groupId);
  }

  protected changeGroup(category: Category, groupId: number | null): void {
    if (groupId === category.groupId) {
      return;
    }
    this.update(category, category.name, groupId);
  }

  private update(category: Category, name: string, groupId: number | null): void {
    this.error.set(null);
    this.service.update(category.id, name, groupId).subscribe({
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
