import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { CategoryGroup } from '../../core/category-group';
import { CategoryGroupsService } from '../../core/category-groups.service';

@Component({
  selector: 'app-category-groups',
  imports: [FormsModule, TableModule, InputTextModule, ButtonModule],
  templateUrl: './category-groups.html',
  styleUrl: './category-groups.scss',
})
export class CategoryGroups {
  private readonly service = inject(CategoryGroupsService);

  protected readonly groups = signal<CategoryGroup[]>([]);
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
      next: (groups) => {
        this.groups.set(groups);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load groups. Is the backend running?');
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

  protected rename(group: CategoryGroup, name: string): void {
    const trimmed = name.trim();
    if (!trimmed || trimmed === group.name) {
      this.load();
      return;
    }
    this.error.set(null);
    this.service.update(group.id, trimmed).subscribe({
      next: () => this.load(),
      error: (err) => {
        this.error.set(this.message(err, `Could not update "${group.name}".`));
        this.load();
      },
    });
  }

  protected remove(group: CategoryGroup): void {
    if (!confirm(`Delete group "${group.name}"? Categories in it become Ungrouped.`)) {
      return;
    }
    this.error.set(null);
    this.service.delete(group.id).subscribe({
      next: () => this.load(),
      error: (err) => this.error.set(this.message(err, `Could not delete "${group.name}".`)),
    });
  }

  private message(err: unknown, fallback: string): string {
    const detail = (err as { error?: { error?: string } })?.error?.error;
    return detail ?? fallback;
  }
}
