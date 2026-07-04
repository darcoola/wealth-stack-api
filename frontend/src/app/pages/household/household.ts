import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TableModule } from 'primeng/table';
import { Member, PartySummary } from '../../core/me';
import { MeService } from '../../core/me.service';
import { PartiesService } from '../../core/parties.service';
import { PartyContextService } from '../../core/auth/party-context.service';

/**
 * Household management: create a shared (ORGANIZATION) party and manage its members. Members are
 * added by the email they signed in with — they must have signed in at least once before.
 */
@Component({
  selector: 'app-household',
  imports: [FormsModule, TableModule, InputTextModule, ButtonModule, SelectModule],
  templateUrl: './household.html',
  styleUrl: './household.scss',
})
export class Household {
  private readonly partiesService = inject(PartiesService);
  private readonly meService = inject(MeService);
  protected readonly partyContext = inject(PartyContextService);

  protected readonly households = computed(() =>
    this.partyContext.parties().filter((p) => p.type === 'ORGANIZATION')
  );
  protected readonly selected = signal<PartySummary | null>(null);
  protected readonly members = signal<Member[]>([]);
  protected readonly error = signal<string | null>(null);

  protected readonly newHouseholdName = signal('');
  protected readonly newMemberEmail = signal('');
  protected readonly newMemberRole = signal<'OWNER' | 'MEMBER'>('MEMBER');
  protected readonly roles = ['MEMBER', 'OWNER'];

  constructor() {
    const first = this.households()[0] ?? null;
    if (first) {
      this.select(first);
    }
  }

  protected isOwner(party: PartySummary | null): boolean {
    return party?.role === 'OWNER';
  }

  protected select(party: PartySummary): void {
    this.selected.set(party);
    this.loadMembers(party.id);
  }

  private loadMembers(partyId: number): void {
    this.error.set(null);
    this.partiesService.members(partyId).subscribe({
      next: (members) => this.members.set(members),
      error: (err) => this.error.set(this.message(err, 'Could not load members.')),
    });
  }

  protected createHousehold(): void {
    const name = this.newHouseholdName().trim();
    if (!name) {
      return;
    }
    this.error.set(null);
    this.partiesService.create(name).subscribe({
      next: () => {
        this.newHouseholdName.set('');
        // The party list comes from /me; refresh it and reload so the switcher picks it up too.
        this.meService.invalidate();
        location.reload();
      },
      error: (err) => this.error.set(this.message(err, `Could not create "${name}".`)),
    });
  }

  protected addMember(): void {
    const party = this.selected();
    const email = this.newMemberEmail().trim();
    if (!party || !email) {
      return;
    }
    this.error.set(null);
    this.partiesService.addMember(party.id, email, this.newMemberRole()).subscribe({
      next: () => {
        this.newMemberEmail.set('');
        this.loadMembers(party.id);
      },
      error: (err) => this.error.set(this.message(err, `Could not add ${email}.`)),
    });
  }

  protected changeRole(member: Member, role: 'OWNER' | 'MEMBER'): void {
    const party = this.selected();
    if (!party || member.role === role) {
      return;
    }
    this.partiesService.changeRole(party.id, member.userId, role).subscribe({
      next: () => this.loadMembers(party.id),
      error: (err) => {
        this.error.set(this.message(err, `Could not change ${member.displayName}'s role.`));
        this.loadMembers(party.id);
      },
    });
  }

  protected removeMember(member: Member): void {
    const party = this.selected();
    if (!party || !confirm(`Remove ${member.displayName} from "${party.name}"?`)) {
      return;
    }
    this.partiesService.removeMember(party.id, member.userId).subscribe({
      next: () => this.loadMembers(party.id),
      error: (err) => this.error.set(this.message(err, `Could not remove ${member.displayName}.`)),
    });
  }

  private message(err: unknown, fallback: string): string {
    const detail = (err as { error?: { error?: string; message?: string } })?.error;
    return detail?.error ?? detail?.message ?? fallback;
  }
}
