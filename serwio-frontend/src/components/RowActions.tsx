import { Pencil, Trash2 } from 'lucide-react';

export function RowActions({ name, onEdit, onDelete }: { name: string; onEdit: () => void; onDelete: () => void }) {
  return (
    <div className="row-actions">
      <button type="button" className="btn-icon" onClick={onEdit} aria-label={`Edytuj: ${name}`} title="Edytuj">
        <Pencil size={14} />
      </button>
      <button type="button" className="btn-icon btn-icon-danger" onClick={onDelete} aria-label={`Usuń: ${name}`} title="Usuń">
        <Trash2 size={14} />
      </button>
    </div>
  );
}
