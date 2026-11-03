import "./App.css";
import useHealthMutation from "./hooks/useHealthMutation";

function App() {
  const { data, isPending, isError, error, refetch } = useHealthMutation();

  if (isPending) return <h1>Connecting with backend...</h1>;

  if (isError) {
    return (
      <div>
        <h1>Error with connection</h1>
        <p>{error.message}</p>
        <button onClick={() => refetch()}> Try again </button>
      </div>
    );
  }

  return <h1>Backend: {data.status}</h1>;
}

export default App;
