import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { TableModule } from 'primeng/table';
import { AccountMapping } from '../../core/account-mapping';
import { AccountMappingsService } from '../../core/account-mappings.service';

@Component({
  selector: 'app-accounts',
  imports: [FormsModule, TableModule, InputTextModule, ButtonModule],
  templateUrl: './accounts.html',
  styleUrl: './accounts.scss',
})
export class Accounts {
  private readonly service = inject(AccountMappingsService);

  protected readonly mappings = signal<AccountMapping[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly newRawAccount = signal('');
  protected readonly newDisplayName = signal('');

  constructor() {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.service.getAll().subscribe({
      next: (mappings) => {
        this.mappings.set(mappings);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load account mappings. Is the backend running?');
        this.loading.set(false);
      },
    });
  }

  protected add(): void {
    const rawAccount = this.newRawAccount().trim();
    const displayName = this.newDisplayName().trim();
    if (!rawAccount || !displayName) {
      return;
    }
    this.error.set(null);
    this.service.create(rawAccount, displayName).subscribe({
      next: () => {
        this.newRawAccount.set('');
        this.newDisplayName.set('');
        this.load();
      },
      error: (err) => this.error.set(this.message(err, `Could not create mapping for "${rawAccount}".`)),
    });
  }

  protected save(mapping: AccountMapping): void {
    const rawAccount = mapping.rawAccount.trim();
    const displayName = mapping.displayName.trim();
    if (!rawAccount || !displayName) {
      this.load();
      return;
    }
    this.error.set(null);
    this.service.update(mapping.id, rawAccount, displayName).subscribe({
      next: () => this.load(),
      error: (err) => {
        this.error.set(this.message(err, `Could not update mapping for "${rawAccount}".`));
        this.load();
      },
    });
  }

  protected remove(mapping: AccountMapping): void {
    if (!confirm(`Delete mapping for "${mapping.rawAccount}"? Its operations revert to the raw account name.`)) {
      return;
    }
    this.error.set(null);
    this.service.delete(mapping.id).subscribe({
      next: () => this.load(),
      error: (err) => this.error.set(this.message(err, `Could not delete mapping for "${mapping.rawAccount}".`)),
    });
  }

  private message(err: unknown, fallback: string): string {
    const detail = (err as { error?: { error?: string } })?.error?.error;
    return detail ?? fallback;
  }
}
