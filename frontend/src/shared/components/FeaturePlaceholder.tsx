import { Link } from "react-router";

type FeaturePlaceholderProps = {
  eyebrow: string;
  title: string;
  description: string;
  tasks: readonly string[];
};

export function FeaturePlaceholder({ eyebrow, title, description, tasks }: FeaturePlaceholderProps) {
  return (
    <section className="page-content">
      <p className="eyebrow">{eyebrow}</p>
      <h1>{title}</h1>
      <p className="lead">{description}</p>
      <div className="content-card feature-card">
        <div className="feature-badge">Planned integration</div>
        <h2>Next engineering milestones</h2>
        <ul className="task-list">
          {tasks.map((task) => <li key={task}>{task}</li>)}
        </ul>
        <Link className="subtle-link" to="/">Back to overview →</Link>
      </div>
    </section>
  );
}
